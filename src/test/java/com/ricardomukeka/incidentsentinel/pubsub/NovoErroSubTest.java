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

import com.ricardomukeka.incidentsentinel.enums.ConfiancaDiagnostico;
import com.ricardomukeka.incidentsentinel.enums.TicketStatus;
import com.ricardomukeka.incidentsentinel.enums.TipoErro;
import com.ricardomukeka.incidentsentinel.investigation.InvestigationBudgetService;
import com.ricardomukeka.incidentsentinel.investigation.InvestigationOrchestrator;
import com.ricardomukeka.incidentsentinel.investigation.InvestigationOutcome;
import com.ricardomukeka.incidentsentinel.investigation.ResultadoOrcamento;
import com.ricardomukeka.incidentsentinel.model.Ticket;
import com.ricardomukeka.incidentsentinel.repository.TicketRepository;
import com.ricardomukeka.incidentsentinel.ticket.TicketService;

@ExtendWith(MockitoExtension.class)
class NovoErroSubTest {

	@Mock
	private TicketRepository ticketRepository;

	@Mock
	private InvestigationBudgetService investigationBudgetService;

	@Mock
	private InvestigationOrchestrator investigationOrchestrator;

	@Mock
	private TicketService ticketService;

	private NovoErroSub novoErroSub;

	@BeforeEach
	void setUp() {
		novoErroSub = new NovoErroSub(ticketRepository, investigationBudgetService, investigationOrchestrator,
				ticketService);
	}

	@Test
	void processar_ticketNaoExiste_naoConsultaOrcamentoNemDelegaParaTicketService() {
		when(ticketRepository.findById(99L)).thenReturn(Optional.empty());

		novoErroSub.processar(99L);

		verify(investigationBudgetService, never()).avaliar(any(Long.class));
		verify(ticketService, never()).marcarDesabilitada(any(Ticket.class));
		verify(ticketService, never()).marcarOrcamentoExcedido(any(Ticket.class));
		verify(ticketService, never()).aplicarDesfechoInvestigacao(any(Ticket.class), any(InvestigationOutcome.class));
	}

	@Test
	void processar_orcamentoDesabilitado_delegaParaMarcarDesabilitada() {
		Ticket ticket = new Ticket();
		ticket.setId(1L);
		ticket.setStatus(TicketStatus.EM_INVESTIGACAO);
		when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
		when(investigationBudgetService.avaliar(1L)).thenReturn(ResultadoOrcamento.DESABILITADO);

		novoErroSub.processar(1L);

		verify(ticketService).marcarDesabilitada(ticket);
		verify(investigationOrchestrator, never()).investigar(any(), any(), any(), any(), any(), any());
	}

	@Test
	void processar_orcamentoExcedido_delegaParaMarcarOrcamentoExcedido() {
		Ticket ticket = new Ticket();
		ticket.setId(2L);
		ticket.setStatus(TicketStatus.EM_INVESTIGACAO);
		when(ticketRepository.findById(2L)).thenReturn(Optional.of(ticket));
		when(investigationBudgetService.avaliar(2L)).thenReturn(ResultadoOrcamento.ORCAMENTO_EXCEDIDO);

		novoErroSub.processar(2L);

		verify(ticketService).marcarOrcamentoExcedido(ticket);
		verify(investigationOrchestrator, never()).investigar(any(), any(), any(), any(), any(), any());
	}

	@Test
	void processar_orcamentoOk_chamaOrchestratorEDelegaParaAplicarDesfecho() {
		Ticket ticket = new Ticket();
		ticket.setId(3L);
		ticket.setStatus(TicketStatus.EM_INVESTIGACAO);
		ticket.setApp("service-orders");
		ticket.setTipoErro(TipoErro.HTTP_5XX);
		ticket.setChaveDedup("x".repeat(64));
		ticket.setTraceId("trace-abc-123");
		when(ticketRepository.findById(3L)).thenReturn(Optional.of(ticket));
		when(investigationBudgetService.avaliar(3L)).thenReturn(ResultadoOrcamento.PROSSEGUIR);
		InvestigationOutcome outcome = new InvestigationOutcome(TicketStatus.ABERTO, "causa raiz real",
				com.ricardomukeka.incidentsentinel.enums.CausaCategoria.INFRA_REDE, ConfiancaDiagnostico.ALTA, "sol",
				ConfiancaDiagnostico.MEDIA, "{\"tsuru_diagnose\":\"consultada\"}");
		when(investigationOrchestrator.investigar(org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq("service-orders"), org.mockito.ArgumentMatchers.eq(TipoErro.HTTP_5XX), org.mockito.ArgumentMatchers.eq("x".repeat(64)), org.mockito.ArgumentMatchers.eq("trace-abc-123"), org.mockito.ArgumentMatchers.any())).thenReturn(outcome);

		novoErroSub.processar(3L);

		verify(ticketService).aplicarDesfechoInvestigacao(ticket, outcome);
	}

	@Test
	void processar_ticketJaEmStatusTerminal_naoReprocessa() {
		Ticket ticket = new Ticket();
		ticket.setId(4L);
		ticket.setStatus(TicketStatus.ABERTO); // redelivery de mensagem já finalizada
		when(ticketRepository.findById(4L)).thenReturn(Optional.of(ticket));

		novoErroSub.processar(4L);

		verify(investigationBudgetService, never()).avaliar(any(Long.class));
		verify(investigationOrchestrator, never()).investigar(any(), any(), any(), any(), any(), any());
		verify(ticketService, never()).aplicarDesfechoInvestigacao(any(), any());
	}
}
