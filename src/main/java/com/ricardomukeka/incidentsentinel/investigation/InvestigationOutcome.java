package com.ricardomukeka.incidentsentinel.investigation;

import com.ricardomukeka.incidentsentinel.enums.CausaCategoria;
import com.ricardomukeka.incidentsentinel.enums.ConfiancaDiagnostico;
import com.ricardomukeka.incidentsentinel.enums.TicketStatus;

/**
 * Resultado de InvestigationOrchestrator.investigar — quem aplica isso ao
 * Ticket é o chamador (NovoErroSub → TicketService), não esta classe.
 */
public record InvestigationOutcome(
		TicketStatus status,
		String causaRaiz,
		CausaCategoria causaCategoria,
		ConfiancaDiagnostico confiancaCausa,
		String solucaoProposta,
		ConfiancaDiagnostico confiancaSolucao,
		String fontesConsultadas) {
}
