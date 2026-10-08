package com.ricardomukeka.incidentsentinel.triage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ricardomukeka.incidentsentinel.dto.ErrorSignalDto;
import com.ricardomukeka.incidentsentinel.enums.SinalOutcome;
import com.ricardomukeka.incidentsentinel.enums.TipoErro;
import com.ricardomukeka.incidentsentinel.ticket.OccurrenceAssociationResult;
import com.ricardomukeka.incidentsentinel.ticket.OccurrenceAssociationService;

/**
 * A partir do passo 9 (docs/ARCHITECTURE.md), a lógica de dedup/transação está em
 * OccurrenceAssociationServiceTest — aqui só a decisão "enfileira ou não"
 * baseada no outcome devolvido.
 */
@ExtendWith(MockitoExtension.class)
class TriageServiceImplTest {

	@Mock
	private OccurrenceAssociationService occurrenceAssociationService;

	@Mock
	private NovoTicketPublisher novoTicketPublisher;

	private TriageServiceImpl triageService;

	private final ErrorSignalDto sinal = new ErrorSignalDto("service-orders", TipoErro.HTTP_5XX,
			Instant.parse("2026-08-20T12:00:00Z"), null, null);

	@BeforeEach
	void setUp() {
		triageService = new TriageServiceImpl(occurrenceAssociationService, novoTicketPublisher);
	}

	@Test
	void processar_novoTicket_enfileiraComOIdRetornado() {
		when(occurrenceAssociationService.associar(sinal))
				.thenReturn(new OccurrenceAssociationResult(SinalOutcome.NOVO_TICKET, 42L));

		triageService.processar(sinal);

		verify(novoTicketPublisher).publicar(42L);
	}

	@Test
	void processar_associado_naoEnfileira() {
		when(occurrenceAssociationService.associar(sinal))
				.thenReturn(new OccurrenceAssociationResult(SinalOutcome.ASSOCIADO, null));

		triageService.processar(sinal);

		verify(novoTicketPublisher, never()).publicar(any(Long.class));
	}
}
