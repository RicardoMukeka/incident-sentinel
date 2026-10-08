# IncidentSentinel — o que monitoramos (para o time de negócio)

> Este documento explica, sem jargão técnico, os 5 tipos de problema que o IncidentSentinel detecta automaticamente. Os nomes entre parênteses (`HTTP_5XX`, `TIMEOUT` etc.) são os "apelidos" que aparecem nos tíquetes gerados pelo sistema — servem só pra identificação rápida, não precisam ser decorados.

## O que está sendo monitorado hoje

Dois serviços de exemplo: **service-orders** (o núcleo de pedidos) e **service-checkout** (que valida os pedidos vindos do service-orders). Cadastrar um sistema novo nessa lista é só uma configuração — sem precisar programar nada de novo.

Pra cada um desses sistemas, o IncidentSentinel observa 5 tipos de problema, a cada 5 minutos, 24 horas por dia.

---

## 1. Erro do servidor (`HTTP_5XX`)

**O que é, em bom português:** o sistema recebeu um pedido e **quebrou tentando processá-lo** — não é que ele demorou ou recusou, ele literalmente falhou no meio do caminho. É o tipo de erro que, se um usuário estivesse na tela, veria uma mensagem de "algo deu errado" ou "erro interno".

**Por que o nome é esse:** toda vez que um sistema responde a um pedido, ele devolve um código de status. Códigos que começam com "5" (500, 502, 503...) são a forma padrão da internet inteira de dizer "o problema é meu, não seu" — daí "5XX".

**Por que importa pro negócio:** é o sintoma mais direto de "algo está quebrado agora". Pode ser um pedido de compra que não foi registrado, uma validação que não rodou — qualquer operação que dependia daquela chamada simplesmente não aconteceu.

---

## 2. Demora além do aceitável (`TIMEOUT`)

**O que é, em bom português:** quem fez o pedido **desistiu de esperar** a resposta, porque o sistema estava demorando demais. Diferente do erro acima, aqui o sistema não necessariamente quebrou — só não respondeu a tempo.

**Por que importa pro negócio:** pro usuário ou pro sistema que estava esperando, o efeito prático é o mesmo de uma falha: a operação não se completa. E timeout costuma ser sintoma de um problema crescendo (banco de dados sobrecarregado, uma dependência lenta) antes de virar uma queda total — pegar isso cedo evita que vire um problema maior.

---

## 3. Falha em outro sistema do qual dependemos (`ERRO_DEPENDENCIA_DOWNSTREAM`)

**O que é, em bom português:** o nosso sistema está funcionando bem, mas ele **depende de outros sistemas** pra completar o trabalho (por exemplo, consultar um catálogo de produtos, validar dados em outro serviço) — e um desses outros sistemas falhou.

**Por que importa pro negócio:** o sintoma aparece no nosso sistema, mas a causa real pode estar em outro time. Sem esse tipo de detecção específica, o time de sustentação perderia tempo procurando o problema no lugar errado. Só o `service-orders` conversa com mais de 25 sistemas diferentes — sem essa distinção, um problema em qualquer um deles viraria um alarme genérico e confuso.

---

## 4. Sistema respondendo, mas devagar demais (`LATENCIA_ANOMALA`)

**O que é, em bom português:** o sistema está funcionando — responde, completa a operação — só que **muito mais devagar do que o normal**. Não é uma falha ainda, é um sinal de alerta.

**Por que importa pro negócio:** é o tipo de detecção mais "preventiva" dos 5. Lentidão anormal costuma ser o primeiro sinal de um problema que, se ignorado, vira uma queda de verdade mais tarde (sobrecarga, vazamento de recursos, etc.). Detectar aqui é detectar antes de virar incidente.

---

## 5. Falha num processamento que roda em segundo plano (`FALHA_PROCESSAMENTO_ASSINCRONO`)

**O que é, em bom português:** existem etapas que não acontecem na hora — o sistema recebe o pedido, responde "ok, recebi" pro usuário, e processa o resto depois, em segundo plano (por exemplo, atualizar um status depois que uma compra é validada). Esse tipo de erro é quando **essa etapa de segundo plano falha**.

**Por que importa pro negócio:** é o tipo de falha mais traiçoeiro dos 5 — porque, na hora, tudo parece ter dado certo (o usuário recebeu uma resposta de sucesso). O problema só aparece depois, quando alguém percebe que uma etapa posterior nunca aconteceu. Sem monitoramento específico pra isso, esse tipo de falha pode ficar invisível por muito tempo.

---

## Resumindo — o que cada um está realmente dizendo

| Apelido técnico | O que está acontecendo, em uma frase |
|---|---|
| `HTTP_5XX` | O sistema quebrou tentando fazer o que foi pedido. |
| `TIMEOUT` | O sistema demorou tanto que quem esperava desistiu. |
| `ERRO_DEPENDENCIA_DOWNSTREAM` | A culpa não é nossa — é de um sistema de outro time do qual dependemos. |
| `LATENCIA_ANOMALA` | Ainda está funcionando, mas devagar o suficiente pra ser um alerta. |
| `FALHA_PROCESSAMENTO_ASSINCRONO` | Deu "certo" na hora, mas uma etapa que deveria acontecer depois, não aconteceu. |

## Por que ter esses 5 tipos, e não só "deu erro"

Cada um desses 5 problemas pede uma ação diferente do time de sustentação — e cada um tem uma causa raiz tipicamente diferente. Ao separar por tipo, o tíquete que chega já vem com um contexto muito mais específico do que "algo deu errado em algum lugar" — o que é exatamente o que o agente de IA usa pra investigar e já entregar um diagnóstico pronto (ver `docs/arquitetura-negocio.drawio`).
