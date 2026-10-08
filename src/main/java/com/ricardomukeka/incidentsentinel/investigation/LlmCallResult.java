package com.ricardomukeka.incidentsentinel.investigation;

import com.ricardomukeka.incidentsentinel.dto.CausaSolucaoResult;

/**
 * Saída de {@link ChatClientInvestigationCaller#gerar} — o structured output
 * do LLM + os metadados de consumo (docs/ARCHITECTURE.md) que viram uma linha
 * de {@code investigation_llm_call}.
 */
public record LlmCallResult(
		CausaSolucaoResult resultado,
		String model,
		long promptTokens,
		long completionTokens,
		long totalTokens) {
}
