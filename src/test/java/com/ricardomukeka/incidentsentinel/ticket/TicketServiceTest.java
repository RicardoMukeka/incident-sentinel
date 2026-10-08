package com.ricardomukeka.incidentsentinel.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ricardomukeka.incidentsentinel.enums.CausaCategoria;
import com.ricardomukeka.incidentsentinel.enums.ConfiancaDiagnostico;
import com.ricardomukeka.incidentsentinel.enums.TicketStatus;
import com.ricardomukeka.incidentsentinel.investigation.InvestigationOutcome;
import com.ricardomukeka.incidentsentinel.model.Ticket;
import com.ricardomukeka.incidentsentinel.repository.TicketRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

	@Mock
	private TicketRepository ticketRepository;

	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

	private TicketService ticketService;

	@BeforeEach
	void setUp() {
		ticketService = new TicketService(ticketRepository, meterRegistry);
	}

	@Test
	void marcarDesabilitada_setaStatusESalvaEIncrementaOutcome() {
		Ticket ticket = new Ticket();
		ticketService.marcarDesabilitada(ticket);
		assertThat(ticket.getStatus()).isEqualTo(TicketStatus.INVESTIGACAO_DESABILITADA);
		verify(ticketRepository).save(ticket);
		assertThat(contadorOutcome("desabilitada")).isEqualTo(1.0);
	}

	@Test
	void marcarOrcamentoExcedido_setaStatusESalvaEIncrementaOutcome() {
		Ticket ticket = new Ticket();
		ticketService.marcarOrcamentoExcedido(ticket);
		assertThat(ticket.getStatus()).isEqualTo(TicketStatus.INVESTIGACAO_PULADA_ORCAMENTO_EXCEDIDO);
		verify(ticketRepository).save(ticket);
		assertThat(contadorOutcome("orcamento_excedido")).isEqualTo(1.0);
	}

	@Test
	void marcarFalhaInfra_setaStatusESalvaEIncrementaOutcome() {
		Ticket ticket = new Ticket();
		ticketService.marcarFalhaInfra(ticket);
		assertThat(ticket.getStatus()).isEqualTo(TicketStatus.INVESTIGACAO_FALHOU_INFRA);
		verify(ticketRepository).save(ticket);
		assertThat(contadorOutcome("falha_infra")).isEqualTo(1.0);
	}

	@Test
	void aplicarDesfechoInvestigacao_aberto_copiaCamposEIncrementaSucesso() {
		Ticket ticket = new Ticket();
		InvestigationOutcome outcome = new InvestigationOutcome(TicketStatus.ABERTO, "causa raiz",
				CausaCategoria.DEPENDENCIA_DOWNSTREAM, ConfiancaDiagnostico.ALTA, "reiniciar o pool",
				ConfiancaDiagnostico.MEDIA, "{\"tsuru_diagnose\":\"consultada\"}");

		ticketService.aplicarDesfechoInvestigacao(ticket, outcome);

		assertThat(ticket.getStatus()).isEqualTo(TicketStatus.ABERTO);
		assertThat(ticket.getCausaRaiz()).isEqualTo("causa raiz");
		assertThat(ticket.getCausaCategoria()).isEqualTo(CausaCategoria.DEPENDENCIA_DOWNSTREAM);
		assertThat(ticket.getConfiancaCausa()).isEqualTo(ConfiancaDiagnostico.ALTA);
		assertThat(ticket.getSolucaoProposta()).isEqualTo("reiniciar o pool");
		assertThat(ticket.getConfiancaSolucao()).isEqualTo(ConfiancaDiagnostico.MEDIA);
		assertThat(ticket.getFontesConsultadas()).isEqualTo("{\"tsuru_diagnose\":\"consultada\"}");
		assertThat(ticket.getInvestigadoEm()).isNotNull();
		verify(ticketRepository).save(ticket);
		assertThat(contadorOutcome("sucesso")).isEqualTo(1.0);
	}

	@Test
	void aplicarDesfechoInvestigacao_causaInconclusiva_incrementaOutcomeInconclusivo() {
		Ticket ticket = new Ticket();
		InvestigationOutcome outcome = new InvestigationOutcome(TicketStatus.CAUSA_INCONCLUSIVA, "causa incerta",
				CausaCategoria.INDETERMINADA, ConfiancaDiagnostico.BAIXA, null, null, "{}");

		ticketService.aplicarDesfechoInvestigacao(ticket, outcome);

		assertThat(contadorOutcome("inconclusivo")).isEqualTo(1.0);
	}

	@Test
	void aplicarDesfechoInvestigacao_falhouParsing_incrementaOutcomeFalhaParsing() {
		Ticket ticket = new Ticket();
		InvestigationOutcome outcome = new InvestigationOutcome(TicketStatus.INVESTIGACAO_FALHOU_PARSING, null, null,
				null, null, null, "{}");

		ticketService.aplicarDesfechoInvestigacao(ticket, outcome);

		assertThat(contadorOutcome("falha_parsing")).isEqualTo(1.0);
	}

	private double contadorOutcome(String outcome) {
		return meterRegistry.counter("incidentsentinel.investigation.outcome", "outcome", outcome).count();
	}
}
