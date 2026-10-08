package com.ricardomukeka.incidentsentinel.enums;

/**
 * Avaliação humana da solução proposta pelo sistema (docs/ARCHITECTURE.md) —
 * gravada quando o humano fecha o ticket. Na v1 é escrita mas não lida; é o
 * corpus que o Failure Book RAG (v2, docs/ARCHITECTURE.md) vai consumir. Default
 * {@link #NAO_AVALIADA}.
 */
public enum AvaliacaoSolucao {
	CONFIRMADA,
	PARCIAL,
	INCORRETA,
	NAO_AVALIADA
}
