package com.ricardomukeka.incidentsentinel.investigation;

/**
 * Desfecho de InvestigationBudgetService.avaliar (docs/ARCHITECTURE.md) — não é
 * um enum persistido (diferente de TicketStatus), só a decisão interna que o
 * chamador (NovoErroSub) traduz pro status do ticket.
 */
public enum ResultadoOrcamento {
	DESABILITADO,
	ORCAMENTO_EXCEDIDO,
	PROSSEGUIR
}
