package com.ricardomukeka.incidentsentinel.ticket;

import com.ricardomukeka.incidentsentinel.enums.CausaCategoria;
import com.ricardomukeka.incidentsentinel.enums.ConfiancaDiagnostico;
import com.ricardomukeka.incidentsentinel.enums.TicketStatus;

/**
 * Campos do ticket derivados do structured output do LLM (docs/ARCHITECTURE.md),
 * já com a redação de segredo aplicada e o gate de baixa confiança resolvido
 * ({@code confiancaCausa == BAIXA} → {@code status = CAUSA_INCONCLUSIVA},
 * {@code solucaoProposta} nula).
 */
public record GeneratedTicketFields(
		TicketStatus status,
		String causaRaiz,
		CausaCategoria causaCategoria,
		ConfiancaDiagnostico confiancaCausa,
		String solucaoProposta,
		ConfiancaDiagnostico confiancaSolucao) {
}
