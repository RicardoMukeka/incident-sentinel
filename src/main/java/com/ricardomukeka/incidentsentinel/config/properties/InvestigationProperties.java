package com.ricardomukeka.incidentsentinel.config.properties;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * docs/ARCHITECTURE.md/§6.4. Controles de custo e de contexto da investigação:
 * <ul>
 * <li>{@code dailyBudget} — teto de INVESTIGAÇÕES no dia (na v1 = 1 chamada de LLM cada).</li>
 * <li>{@code dailyCostCapUsd} — 2ª trava do orçamento, agora em US$ (docs/ARCHITECTURE.md): duas
 * investigações podem custar 10x diferente; o contador não vê isso. Soft cap — pega a próxima
 * investigação, não a atual.</li>
 * <li>{@code enabled} — interruptor duro do caminho de LLM.</li>
 * <li>{@code traceMaxSpans}/{@code logMaxEntries}/{@code logWindow} — tetos de destilação dos
 * retrievers (quanto de cada fonte entra no prompt).</li>
 * <li>{@code promptMaxChars} — hard cap no prompt montado, rede de segurança contra caso patológico
 * (mensagens de exceção gigantes) inflando o input mesmo com a contagem limitada.</li>
 * <li>{@code retrieverTimeout} — timeout por retriever no fan-out paralelo; sem ele um subprocesso
 * MCP travado trava a investigação inteira ({@code .join()} espera pra sempre).</li>
 * <li>{@code llmTimeout} — timeout da chamada ao ChatClient.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "incidentsentinel.investigation")
public record InvestigationProperties(
		long dailyBudget,
		BigDecimal dailyCostCapUsd,
		boolean enabled,
		int traceMaxSpans,
		int logMaxEntries,
		Duration logWindow,
		int promptMaxChars,
		Duration retrieverTimeout,
		Duration llmTimeout) {
}
