package com.ricardomukeka.incidentsentinel.pubsub;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ricardomukeka.incidentsentinel.model.Ticket;
import com.ricardomukeka.incidentsentinel.repository.TicketRepository;
import com.ricardomukeka.incidentsentinel.ticket.TicketService;

@ExtendWith(MockitoExtension.class)
class NovoErroDLTSubTest {

	@Mock
	private TicketRepository ticketRepository;

	@Mock
	private TicketService ticketService;

	private NovoErroDLTSub novoErroDLTSub;

	@BeforeEach
	void setUp() {
		novoErroDLTSub = new NovoErroDLTSub(ticketRepository, ticketService);
	}

	@Test
	void marcarFalhaInfra_ticketExiste_delegaParaTicketService() {
		Ticket ticket = new Ticket();
		ticket.setId(7L);
		when(ticketRepository.findById(7L)).thenReturn(Optional.of(ticket));

		novoErroDLTSub.marcarFalhaInfra(7L);

		verify(ticketService).marcarFalhaInfra(ticket);
	}

	@Test
	void marcarFalhaInfra_ticketNaoExiste_naoDelegaParaTicketService() {
		when(ticketRepository.findById(99L)).thenReturn(Optional.empty());

		novoErroDLTSub.marcarFalhaInfra(99L);

		verify(ticketService, never()).marcarFalhaInfra(any(Ticket.class));
	}
}
