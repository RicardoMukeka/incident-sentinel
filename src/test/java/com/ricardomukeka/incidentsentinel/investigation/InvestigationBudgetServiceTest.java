package com.ricardomukeka.incidentsentinel.investigation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ricardomukeka.incidentsentinel.config.properties.InvestigationProperties;
import com.ricardomukeka.incidentsentinel.model.InvestigationBudget;
import com.ricardomukeka.incidentsentinel.repository.InvestigationBudgetRepository;
import com.ricardomukeka.incidentsentinel.repository.TicketRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class InvestigationBudgetServiceTest {

	@Mock
	private InvestigationBudgetRepository investigationBudgetRepository;

	@Mock
	private TicketRepository ticketRepository;

	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

	private InvestigationBudgetService service;

	private static InvestigationProperties props(long dailyBudget, boolean enabled, String costCapUsd) {
		return new InvestigationProperties(dailyBudget, new java.math.BigDecimal(costCapUsd), enabled, 40, 50,
				java.time.Duration.ofMinutes(15), 20000, java.time.Duration.ofSeconds(25),
				java.time.Duration.ofSeconds(60));
	}

	@BeforeEach
	void setUp() {
		// dailyBudget=5, cost cap alto (não interfere), nos testes que chegam a checar o teto
		service = new InvestigationBudgetService(investigationBudgetRepository, ticketRepository,
				props(5, true, "999"), meterRegistry);
	}

	@Test
	void avaliar_flagDesabilitada_nemTocaNoOrcamento() {
		InvestigationBudgetService desabilitado = new InvestigationBudgetService(investigationBudgetRepository,
				ticketRepository, props(5, false, "999"), meterRegistry);

		ResultadoOrcamento resultado = desabilitado.avaliar(1L);

		assertThat(resultado).isEqualTo(ResultadoOrcamento.DESABILITADO);
		verify(ticketRepository, never()).marcarOrcamentoDebitado(any(Long.class));
		verify(investigationBudgetRepository, never()).incrementarContador(any(LocalDate.class));
	}

	@Test
	void avaliar_redelivery_orcamentoJaDebitado_naoIncrementaDeNovo() {
		when(ticketRepository.marcarOrcamentoDebitado(1L)).thenReturn(0);

		ResultadoOrcamento resultado = service.avaliar(1L);

		assertThat(resultado).isEqualTo(ResultadoOrcamento.PROSSEGUIR);
		verify(investigationBudgetRepository, never()).incrementarContador(any(LocalDate.class));
	}

	@Test
	void avaliar_primeiraVez_dentroDoOrcamento_prossegue() {
		when(ticketRepository.marcarOrcamentoDebitado(1L)).thenReturn(1);
		InvestigationBudget budget = new InvestigationBudget();
		budget.setContador(3L);
		when(investigationBudgetRepository.findById(any(LocalDate.class))).thenReturn(Optional.of(budget));

		ResultadoOrcamento resultado = service.avaliar(1L);

		assertThat(resultado).isEqualTo(ResultadoOrcamento.PROSSEGUIR);
		verify(investigationBudgetRepository).garantirLinhaDoDia(any(LocalDate.class));
		verify(investigationBudgetRepository).incrementarContador(any(LocalDate.class));
	}

	@Test
	void avaliar_primeiraVez_estourouOTeto_orcamentoExcedido() {
		when(ticketRepository.marcarOrcamentoDebitado(1L)).thenReturn(1);
		InvestigationBudget budget = new InvestigationBudget();
		budget.setContador(6L); // > dailyBudget (5)
		when(investigationBudgetRepository.findById(any(LocalDate.class))).thenReturn(Optional.of(budget));

		ResultadoOrcamento resultado = service.avaliar(1L);

		assertThat(resultado).isEqualTo(ResultadoOrcamento.ORCAMENTO_EXCEDIDO);
	}

	@Test
	void avaliar_dentroDaContagemMasEstourouOCustoUsd_orcamentoExcedido() {
		InvestigationBudgetService comCapBaixo = new InvestigationBudgetService(investigationBudgetRepository,
				ticketRepository, props(100, true, "2.00"), meterRegistry);
		when(ticketRepository.marcarOrcamentoDebitado(1L)).thenReturn(1);
		InvestigationBudget budget = new InvestigationBudget();
		budget.setContador(3L); // dentro da contagem
		budget.setCustoAcumuladoUsd(new java.math.BigDecimal("2.50")); // >= cap de 2.00
		when(investigationBudgetRepository.findById(any(LocalDate.class))).thenReturn(Optional.of(budget));

		assertThat(comCapBaixo.avaliar(1L)).isEqualTo(ResultadoOrcamento.ORCAMENTO_EXCEDIDO);
	}

	@Test
	void avaliar_incrementaAntesDeLer_nuncaLeAntesDeIncrementar() {
		when(ticketRepository.marcarOrcamentoDebitado(1L)).thenReturn(1);
		when(investigationBudgetRepository.findById(any(LocalDate.class)))
				.thenReturn(Optional.of(umBudgetCom(5L)));

		service.avaliar(1L);

		org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(investigationBudgetRepository);
		ordem.verify(investigationBudgetRepository).incrementarContador(any(LocalDate.class));
		ordem.verify(investigationBudgetRepository).findById(any(LocalDate.class));
	}

	private InvestigationBudget umBudgetCom(long contador) {
		InvestigationBudget budget = new InvestigationBudget();
		budget.setContador(contador);
		return budget;
	}
}
