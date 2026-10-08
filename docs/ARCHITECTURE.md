# Arquitetura

## Visão geral

```
Coletor ──► Triagem (dedup) ──► Fila (Pub/Sub) ──► Orçamento diário ──► Retrieval ──► LLM (1 chamada) ──► Tíquete
```

Disparado por HTTP a cada 5 minutos (um scheduler externo chama `POST /internal/collector/run` — deliberadamente não `@Scheduled`, pra não acoplar o agendamento ao ciclo de vida do processo).

## Componentes

- **Coletor** (`collector/`) — para cada serviço monitorado × 5 tipos de erro, calcula um watermark (maior timestamp já processado, por serviço+tipo — não agregado) e busca sinais novos via SigNoz REST (`/api/v5/query_range`). As N consultas rodam em paralelo via virtual threads; falha isolada em um serviço/tipo não aborta o ciclo inteiro.
- **Triagem** (`triage/`) — dedup via `INSERT ... ON DUPLICATE KEY UPDATE` nativo, chave = SHA-256 de `serviço|tipo_erro|atributo_extra`. As duas escritas que compõem uma ocorrência nova (tíquete + log do sinal) são uma transação só — isso é o que torna o watermark derivado seguro contra race condition.
- **Fila** (`pubsub/`) — a mensagem publicada é só o id numérico do tíquete; canal principal com ack manual, dead-letter topic com ack automático.
- **Orçamento diário** (`investigation/InvestigationBudgetService`) — duas travas independentes, a que disparar primeiro vence: contagem de investigações no dia, OU custo acumulado em US$ (soft cap — pega a próxima investigação, não interrompe a corrente). Guarda de idempotência contra debitar o orçamento duas vezes pro mesmo tíquete. Incremento atômico de contador + tokens + custo, bindado por dia em UTC pela JVM (nunca `CURRENT_DATE`/`NOW()` do banco, pra não divergir perto da meia-noite).
- **Retrieval** (`investigation/retriever/`) — três fontes, buscadas em paralelo, cada uma devolvendo um resumo **destilado** (não o payload bruto — um trace de centenas de spans vira um resumo de dezenas; logs verbosos viram só as linhas relevantes):
  - **trace distribuído** — REST, filtro por trace id, com reconstrução de hierarquia pai/filho e dedup de span.
  - **logs estruturados** — biblioteca oficial do provedor de logging, filtro por trace id/severidade.
  - **diagnóstico de plataforma** — via MCP (Model Context Protocol), cliente usado de forma **programática** (nunca exposto a um `ChatClient` com tool-calling).
  Falha isolada por fonte fica registrada (quais fontes contribuíram pra aquela investigação), não aborta as outras.
- **Orquestrador da investigação + chamador do LLM** (`investigation/InvestigationOrchestrator`, `ChatClientInvestigationCaller`) — **uma chamada** ao modelo, **sem tools**. Fan-out das 3 fontes com timeout individual por `CompletableFuture`; um serviço de redação remove segredo da evidência **antes** de montar o prompt; hard cap no tamanho do prompt montado. A chamada de rede ao modelo passa por retry + circuit breaker + timeout — falha aí é tratada como erro de infraestrutura (propaga pra nack/redelivery da mensagem). A conversão da resposta de texto pro objeto estruturado é feita **fora** desse wrapper de resiliência — falha aí é erro de parsing, tratado como outcome de negócio (`FALHOU_PARSING`), nunca re-tentado às ciegas.
- **Geração do tíquete** (`ticket/TicketGenerationService`) — aplica o gate de confiança: diagnóstico de confiança baixa não propõe solução (`CAUSA_INCONCLUSIVA`). O serviço de redação roda de novo aqui, na saída do modelo, antes de persistir.
- **API** — `GET /tickets` (paginado, filtrável por serviço/status/data) e `GET /tickets/{id}` (404 como `ProblemDetail`, RFC 9457).

## Decisões de design

### Por que retrieval 100% determinístico, e não um agente com ferramentas?

A primeira versão do design dava ao modelo acesso a um conjunto de ferramentas (ex. busca em repositório de código) e deixava **ele** decidir o que buscar, num loop de tool-calling. Isso foi abandonado por três motivos:

- **Custo e previsibilidade.** Cada decisão do modelo de "o que buscar a seguir" é mais uma rodada de LLM cobrada, com o contexto acumulado sendo reenviado a cada iteração — custo não-linear e difícil de prever com antecedência. Previsibilidade de custo foi sempre a prioridade #1.
- **Superfície de ataque.** Dar a um modelo acesso a ferramentas com efeitos colaterais (mesmo que só de leitura em teoria) exige allow-listing cuidadoso e aumenta a superfície de prompt injection. Reduzir pra "zero tools, structured output puro" elimina essa classe de risco inteira nesse caminho.
- **O buraco real estava em outro lugar.** Nem o trace nem o diagnóstico de plataforma traziam a **linha de log que a aplicação de fato imprimiu** (a mensagem de erro, o stack trace). Preencher esse buraco com uma terceira fonte determinística (logs) resolveu a maior parte dos casos que antes "escalariam" pra uma busca mais cara.

**Princípio resultante:** use retrieval determinístico (REST/lib) quando a busca é mecânica e a fonte é bem documentada. Só vale a pena manter uma ferramenta (MCP) viva quando ela encapsula lógica real que seria caro reimplementar — nesse projeto, só o diagnóstico de plataforma se qualifica; as outras duas fontes eram só wrappers finos de uma chamada REST que já dava pra fazer direto.

### Por que uma chamada de LLM, sem tools?

Consequência direta da decisão acima: sem ferramenta nenhuma pro modelo decidir usar, a chamada vira *structured output* puro — `evidência destilada → { causa_raiz, categoria, confiança_causa, solução_proposta, confiança_solução }`. Cai o loop de tool-calling e todo o mecanismo de teto de iterações que ele exigiria. O gate de confiança (`confiança_causa == BAIXA` → não propor solução) não é "pular uma segunda chamada" — só existe uma.

### Por que redação de segredo em dois pontos, não um?

Evidência de trace/log/diagnóstico pode conter, por acidente, uma credencial que uma aplicação monitorada logou em texto plano (acontece mais do que se gostaria). Redigir só a **saída** do modelo (antes de persistir o tíquete) não protege contra isso — o segredo já teria sido enviado pra um provedor externo de LLM antes mesmo de chegar nesse ponto. Por isso a redação roda duas vezes: na evidência, antes de montar o prompt (ponto novo, mais importante), e na saída do modelo, antes de persistir (defesa em profundidade). Um contador de métrica incrementado quando a redação de entrada efetivamente altera algo funciona como sinal operacional: se ele disparar com frequência, é sinal de que algum serviço monitorado está logando credencial em texto plano — vale investigar na origem, não só confiar na rede de segurança.

### Por que separar falha de rede de falha de parsing?

Uma falha ao **chamar** o modelo (timeout, circuit breaker aberto, erro de rede) é um problema de infraestrutura — vale a pena deixar a mensagem voltar pra fila e tentar de novo. Uma falha ao **interpretar** a resposta do modelo (saída que não bate com o schema esperado) é outra categoria — tentar de novo com a mesma entrada provavelmente dá o mesmo resultado, então é tratada como um outcome de negócio (`FALHOU_PARSING`) e registrada, não re-tentada automaticamente. Misturar as duas por baixo do mesmo bloco de retry esconderia qual das duas realmente está acontecendo quando a métrica de falha sobe.

### Resiliência composta programaticamente, não anotada

Toda chamada de rede (API de métricas, MCP, logging, chamada ao modelo) é protegida por retry + circuit breaker montados programaticamente — nunca via anotação `@Retry`/`@CircuitBreaker`. Isso evita acoplar o projeto a tecelagem de aspecto (AOP) pra uma decisão que é, no fundo, só "componha esses três objetos e chame o método" — e deixa o comportamento de resiliência testável sem subir um contexto Spring inteiro.

## O que fica de fora de propósito (por agora)

Um "livro de falhas" (recuperar diagnósticos anteriores parecidos via busca semântica, RAG) e a separação da investigação em dois agentes (um pra causa, outro pra solução) foram avaliados e adiados deliberadamente. A separação em dois agentes só se justificaria pela ordem que um RAG exigiria (recuperar precedentes pela causa diagnosticada → só então propor solução); sem esse corpus de precedentes, as duas tarefas cabem numa chamada só. Os campos de feedback humano sobre a qualidade do diagnóstico já existem no schema — gravados, não lidos — justamente para acumular esse corpus antes de essa decisão precisar ser revisitada.
