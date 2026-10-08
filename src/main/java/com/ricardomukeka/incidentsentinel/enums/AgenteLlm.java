package com.ricardomukeka.incidentsentinel.enums;

/**
 * Qual etapa de LLM gerou uma linha de {@code investigation_llm_call}
 * (docs/ARCHITECTURE.md). Na v1 há uma única chamada por investigação
 * ({@link #INVESTIGADOR}); a v2 (docs/ARCHITECTURE.md) separa em
 * {@link #ANALISTA} (causa) e {@link #SOLUCAO}.
 */
public enum AgenteLlm {
	INVESTIGADOR,
	ANALISTA,
	SOLUCAO
}
