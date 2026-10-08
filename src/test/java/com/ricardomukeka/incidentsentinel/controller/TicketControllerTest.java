package com.ricardomukeka.incidentsentinel.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.ricardomukeka.incidentsentinel.enums.ConfiancaDiagnostico;
import com.ricardomukeka.incidentsentinel.enums.TicketStatus;
import com.ricardomukeka.incidentsentinel.enums.TipoErro;
import com.ricardomukeka.incidentsentinel.model.Ticket;
import com.ricardomukeka.incidentsentinel.repository.TicketRepository;

/**
 * @WebMvcTest usa Application.class como fonte de config — como ela tem
 * @EnableFeignClients, o SignozQueryClient é registrado eagerly mesmo num
 * slice de teste, exigindo signoz.api.base-url resolvido (achado real ao
 * rodar este teste, não hipotético). Property mínima só pra isso não
 * quebrar; nenhuma chamada HTTP de verdade acontece neste teste.
 */
@WebMvcTest(TicketController.class)
@TestPropertySource(properties = "signoz.api.base-url=http://localhost")
class TicketControllerTest {

	@Autowired
	private MockMvcTester mvc;

	@MockitoBean
	private TicketRepository ticketRepository;

	private Ticket ticketDeExemplo() {
		Ticket ticket = new Ticket();
		ticket.setId(1L);
		ticket.setApp("service-orders");
		ticket.setTipoErro(TipoErro.HTTP_5XX);
		ticket.setChaveDedup("x".repeat(64));
		ticket.setData(LocalDate.of(2026, 8, 20));
		ticket.setStatus(TicketStatus.ABERTO);
		ticket.setCausaRaiz("causa raiz de teste");
		ticket.setConfiancaCausa(ConfiancaDiagnostico.ALTA);
		ticket.setFontesConsultadas("{}");
		ticket.setPrimeiraOcorrenciaTs(Instant.parse("2026-08-20T12:00:00Z"));
		ticket.setUltimaOcorrenciaTs(Instant.parse("2026-08-20T12:05:00Z"));
		ticket.setContagemOcorrencias(3);
		ticket.setOrcamentoDebitado(true);
		return ticket;
	}

	@Test
	void listar_devolve200ComTicketsPaginados() {
		Page<Ticket> pagina = new PageImpl<>(List.of(ticketDeExemplo()), PageRequest.of(0, 20), 1);
		when(ticketRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
				.thenReturn(pagina);

		assertThat(mvc.get().uri("/tickets")).hasStatusOk().bodyJson().extractingPath("$.content[0].app")
				.isEqualTo("service-orders");
	}

	@Test
	void detalhar_ticketExiste_devolve200ComCausaRaiz() {
		when(ticketRepository.findById(1L)).thenReturn(java.util.Optional.of(ticketDeExemplo()));

		assertThat(mvc.get().uri("/tickets/1")).hasStatusOk().bodyJson().extractingPath("$.causaRaiz")
				.isEqualTo("causa raiz de teste");
	}

	@Test
	void detalhar_ticketNaoExiste_devolve404ComProblemDetail() {
		when(ticketRepository.findById(99L)).thenReturn(java.util.Optional.empty());

		assertThat(mvc.get().uri("/tickets/99")).hasStatus(404);
	}
}
