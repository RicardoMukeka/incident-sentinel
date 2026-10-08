package com.ricardomukeka.incidentsentinel.triage;

import org.springframework.stereotype.Service;

import com.ricardomukeka.incidentsentinel.dto.ErrorSignalDto;
import com.ricardomukeka.incidentsentinel.enums.SinalOutcome;
import com.ricardomukeka.incidentsentinel.ticket.OccurrenceAssociationResult;
import com.ricardomukeka.incidentsentinel.ticket.OccurrenceAssociationService;

/**
 * Triagem determinística, sem LLM (docs/ARCHITECTURE.md). A partir do passo 9
 * (docs/ARCHITECTURE.md), a dedup/gravação transacional foi extraída pra
 * OccurrenceAssociationService — esta classe só decide o que fazer com o
 * desfecho (enfileirar se for ticket novo), sem mexer em transação/banco
 * diretamente.
 *
 * <p>Isolamento por sinal (try/catch individual no lote) continua
 * responsabilidade do chamador (CollectorService.processarComIsolamento) —
 * este método assume que já está sendo chamado um sinal por vez.
 */
@Service
public class TriageServiceImpl implements TriageService {

	private final OccurrenceAssociationService occurrenceAssociationService;
	private final NovoTicketPublisher novoTicketPublisher;

	public TriageServiceImpl(OccurrenceAssociationService occurrenceAssociationService,
			NovoTicketPublisher novoTicketPublisher) {
		this.occurrenceAssociationService = occurrenceAssociationService;
		this.novoTicketPublisher = novoTicketPublisher;
	}

	@Override
	public void processar(ErrorSignalDto sinal) {
		OccurrenceAssociationResult resultado = occurrenceAssociationService.associar(sinal);

		if (resultado.outcome() == SinalOutcome.NOVO_TICKET) {
			novoTicketPublisher.publicar(resultado.ticketId());
		}
	}
}
