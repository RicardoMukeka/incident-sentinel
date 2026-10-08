package com.ricardomukeka.incidentsentinel.investigation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ricardomukeka.incidentsentinel.config.properties.InvestigationProperties;
import com.ricardomukeka.incidentsentinel.model.InvestigationBudget;
import com.ricardomukeka.incidentsentinel.repository.InvestigationBudgetRepository;
import com.ricardomukeka.incidentsentinel.repository.TicketRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Orçamento diário do LLM (docs/ARCHITECTURE.md). Ordem fixa: (1) feature flag —
 * interruptor duro, nem toca no orçamento se desligada; (2) guarda de
 * idempotência (orcamento_debitado) — redelivery da mesma mensagem não conta
 * 2x; (3) incremento atômico + leitura do contador resultante NA MESMA
 * transação (MySQL não suporta RETURNING em UPDATE) pra decidir se estourou
 * o teto. Nunca lê o contador antes de incrementar.
 *
 * <p>{@code contador} conta INVESTIGAÇÕES (na v1 = 1 chamada de LLM; na v2
 * uma investigação pode ter 2). {@code registrarConsumo} é chamado por
 * chamada de LLM ({@link LlmCallRecorder}) e faz o rollup diário de tokens +
 * custo em US$.
 */
@Service
public class InvestigationBudgetService {

	private static final Logger log = LoggerFactory.getLogger(InvestigationBudgetService.class);

	private final InvestigationBudgetRepository investigationBudgetRepository;
	private final TicketRepository ticketRepository;
	private final InvestigationProperties investigationProperties;

	public InvestigationBudgetService(InvestigationBudgetRepository investigationBudgetRepository,
			TicketRepository ticketRepository, InvestigationProperties investigationProperties,
			MeterRegistry meterRegistry) {
		this.investigationBudgetRepository = investigationBudgetRepository;
		this.ticketRepository = ticketRepository;
		this.investigationProperties = investigationProperties;

		// Tokens e custo lidos do banco a cada scrape — não acumulados em memória do
		// processo (docs/ARCHITECTURE.md: um Gauge in-memory por réplica mostraria só a fração
		// daquela réplica, mesmo bug de estado não-centralizado do orçamento).
		Gauge.builder("incidentsentinel.investigation.tokens.acumulados", investigationBudgetRepository,
				repo -> lerDoDia(repo, b -> b.getTokensAcumulados() == null ? 0.0 : b.getTokensAcumulados().doubleValue()))
				.description("Tokens consumidos pelo LLM no dia corrente (UTC), lido do banco")
				.register(meterRegistry);
		Gauge.builder("incidentsentinel.investigation.custo.acumulado.usd", investigationBudgetRepository,
				repo -> lerDoDia(repo, b -> b.getCustoAcumuladoUsd() == null ? 0.0 : b.getCustoAcumuladoUsd().doubleValue()))
				.description("Custo estimado do LLM no dia corrente (UTC) em US$, lido do banco")
				.register(meterRegistry);
	}

	private static double lerDoDia(InvestigationBudgetRepository repo,
			java.util.function.ToDoubleFunction<InvestigationBudget> extrator) {
		return repo.findById(LocalDate.now(ZoneOffset.UTC)).map(extrator::applyAsDouble).orElse(0.0);
	}

	@Transactional
	public ResultadoOrcamento avaliar(Long ticketId) {
		if (!investigationProperties.enabled()) {
			return ResultadoOrcamento.DESABILITADO;
		}

		int primeiraVez = ticketRepository.marcarOrcamentoDebitado(ticketId);
		if (primeiraVez == 0) {
			// Redelivery de uma mensagem cujo orçamento já foi debitado — segue direto
			// pro reprocessamento, sem contar de novo.
			return ResultadoOrcamento.PROSSEGUIR;
		}

		LocalDate hoje = LocalDate.now(ZoneOffset.UTC);
		investigationBudgetRepository.garantirLinhaDoDia(hoje);
		investigationBudgetRepository.incrementarContador(hoje);

		InvestigationBudget doDia = investigationBudgetRepository.findById(hoje).orElse(null);
		long contadorAtual = doDia == null || doDia.getContador() == null ? 0L : doDia.getContador();
		java.math.BigDecimal custoAtual = doDia == null || doDia.getCustoAcumuladoUsd() == null
				? java.math.BigDecimal.ZERO : doDia.getCustoAcumuladoUsd();

		if (contadorAtual > investigationProperties.dailyBudget()) {
			log.warn("Orçamento diário excedido (contagem): {} > teto de {} — ticket {} pulado",
					contadorAtual, investigationProperties.dailyBudget(), ticketId);
			return ResultadoOrcamento.ORCAMENTO_EXCEDIDO;
		}

		// 2ª trava, em US$ (docs/ARCHITECTURE.md) — soft cap: o custo da investigação atual só é
		// conhecido depois da chamada de LLM, então esta checa o acumulado ATÉ a anterior e pega a
		// próxima. Ainda assim transforma "custo observável" em "custo com freio".
		java.math.BigDecimal capUsd = investigationProperties.dailyCostCapUsd();
		if (capUsd != null && capUsd.signum() > 0 && custoAtual.compareTo(capUsd) >= 0) {
			log.warn("Orçamento diário excedido (custo): US$ {} >= teto de US$ {} — ticket {} pulado",
					custoAtual, capUsd, ticketId);
			return ResultadoOrcamento.ORCAMENTO_EXCEDIDO;
		}

		return ResultadoOrcamento.PROSSEGUIR;
	}

	/** Rollup diário — chamado a cada chamada de LLM (docs/ARCHITECTURE.md), 1 por investigação na v1. */
	@Transactional
	public void registrarConsumo(long tokens, BigDecimal custoUsd) {
		LocalDate hoje = LocalDate.now(ZoneOffset.UTC);
		investigationBudgetRepository.garantirLinhaDoDia(hoje);
		investigationBudgetRepository.incrementarConsumo(hoje, tokens,
				custoUsd == null ? BigDecimal.ZERO : custoUsd);
	}
}
