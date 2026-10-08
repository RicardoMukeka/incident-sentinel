# IncidentSentinel

Observabilidade + IA aplicada a operação de software: monitora serviços Java via [SigNoz](https://signoz.io/), detecta padrões de erro conhecidos, e — para cada ocorrência nova do dia — reúne evidência de múltiplas fontes (trace distribuído, logs estruturados, diagnóstico de plataforma) e faz **uma única chamada a um modelo de linguagem** para propor causa raiz e solução, registrando tudo em um tíquete.

> Projeto de portfólio, extraído e generalizado a partir de um sistema real que desenvolvi para monitorar microsserviços Java em produção. Nomes de serviços e infraestrutura foram trocados por exemplos genéricos; a arquitetura, as decisões de design e o código são os mesmos.

## O problema que isso resolve

Times de sustentação gastam tempo desproporcional fazendo o mesmo trabalho manual toda vez que um erro aparece: abrir o APM, achar o trace, cruzar com os logs, olhar o estado da infraestrutura, e só então começar a investigar a causa. O **IncidentSentinel** automatiza a parte de coleta e dá um primeiro diagnóstico — o time humano entra já com contexto, não do zero.

## Como funciona, em uma frase por etapa

```
Coletor (SigNoz) → Triagem/dedup → Fila (Pub/Sub) → Orçamento diário
   → Retrieval (3 fontes, paralelo, determinístico) → 1 chamada de LLM → Tíquete
```

1. **Coletor** — a cada 5 minutos, consulta o SigNoz procurando 5 tipos de problema (erro 5xx, timeout, dependência externa falhando, latência anômala, falha em processamento assíncrono) em cada serviço monitorado.
2. **Triagem** — deduplica por uma chave determinística (hash de serviço + tipo de erro + atributo), evitando abrir tíquete duplicado para o mesmo incidente.
3. **Fila** — cada ocorrência nova entra numa fila (Pub/Sub) para processamento assíncrono, com DLT para falhas permanentes.
4. **Orçamento diário** — duas travas independentes (número de investigações E custo acumulado em US$) protegem contra gasto descontrolado com chamadas de LLM.
5. **Retrieval determinístico** — três fontes de evidência são buscadas **em paralelo**, cada uma com timeout e circuit breaker próprios, e cada uma devolve um resumo **destilado** (não o payload bruto):
   - trace distribuído (REST, SigNoz)
   - logs estruturados (lib oficial de logging, filtrados por trace id)
   - diagnóstico de plataforma (MCP, única ferramenta exposta — e nunca a um `ChatClient` com tool-calling)
6. **Uma chamada de LLM, sem tools** — a evidência destilada e redigida (segredos removidos) vira um prompt único; o modelo devolve causa raiz, categoria, confiança e solução proposta em formato estruturado. Sem loop de tool-calling, sem custo não-determinístico.
7. **Tíquete** — persistido com o diagnóstico, mais um gate de confiança (diagnóstico de baixa confiança não propõe solução).

Mais detalhes de cada etapa, incluindo as decisões de design e os porquês, em [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Destaques técnicos

- **Custo de LLM sob controle**: orçamento diário com trava dupla (contagem e custo em US$), idempotência contra re-pagamento em redelivery de mensagem, e snapshot de preço por chamada para custo histórico estável.
- **Resiliência composta, não anotada**: retry + circuit breaker (Resilience4j) montados programaticamente — nunca via `@Retry`/`@CircuitBreaker` — com timeout por fonte no fan-out paralelo dos retrievers.
- **Defesa em profundidade contra segredo em prompt**: um serviço de redação roda **duas vezes** — na evidência, antes de montar o prompt (para não mandar credencial pra um provedor externo de LLM), e na saída do modelo, antes de persistir.
- **Separação de falha de rede vs. falha de parsing**: erro na chamada ao modelo (timeout, circuit breaker aberto) é tratado como falha de infraestrutura e cai pra retry/redelivery; erro ao interpretar a resposta do modelo é tratado como outcome de negócio (`FALHOU_PARSING`), nunca re-tentado às ciegas.
- **Virtual threads** para um workload I/O-bound (chamadas HTTP, subprocesso MCP, chamada de LLM de vários segundos).
- **Timezone único (UTC)** na JVM e no banco, pra fronteira de "dia" do orçamento/dedup não divergir perto da meia-noite.

## Stack

Java 21 · Spring Boot 4 · Spring AI (Gemini via Vertex AI) · MySQL + Liquibase · Google Cloud Pub/Sub · Google Cloud Logging · Resilience4j · MCP (Model Context Protocol) · SigNoz (OpenTelemetry) · Testcontainers

## Rodando localmente

```bash
mvn compile
mvn test
mvn spring-boot:run
```

Variáveis de ambiente necessárias (host do MySQL, credenciais, endpoint do SigNoz, projeto GCP etc.) estão documentadas em `src/main/resources/application.properties` — todas lidas via `${VAR}`, nenhum valor real está versionado.

Dois testes de concorrência (`InvestigationBudgetConcurrencyTest`) usam Testcontainers + MySQL real e são pulados automaticamente sem Docker disponível.

## Documentação

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — arquitetura, componentes e decisões de design.
- [`docs/o-que-monitoramos.md`](docs/o-que-monitoramos.md) — os 5 tipos de erro detectados, explicados sem jargão.
- `docs/*.drawio` — diagramas (arquitetura macro, técnica, de negócio, e modelo de dados). Abrir com [draw.io](https://app.diagrams.net/) ou a extensão correspondente no VS Code.

## Autor

[Ricardo Mukeka](https://github.com/RicardoMukeka)
