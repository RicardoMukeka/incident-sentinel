package com.ricardomukeka.incidentsentinel.investigation;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ricardomukeka.incidentsentinel.config.properties.PricingProperties;
import com.ricardomukeka.incidentsentinel.enums.AgenteLlm;
import com.ricardomukeka.incidentsentinel.model.InvestigationLlmCall;
import com.ricardomukeka.incidentsentinel.repository.InvestigationLlmCallRepository;

/**
 * Grava uma linha de {@code investigation_llm_call} por chamada de LLM
 * (docs/ARCHITECTURE.md) e incrementa o rollup diário
 * ({@link InvestigationBudgetService#registrarConsumo}). {@code
 * custo_estimado_usd} = tokens × preço-da-época de {@link PricingProperties}
 * — snapshot, custo histórico estável.
 */
@Component
public class LlmCallRecorder {

	private final InvestigationLlmCallRepository investigationLlmCallRepository;
	private final InvestigationBudgetService investigationBudgetService;
	private final PricingProperties pricingProperties;

	public LlmCallRecorder(InvestigationLlmCallRepository investigationLlmCallRepository,
			InvestigationBudgetService investigationBudgetService, PricingProperties pricingProperties) {
		this.investigationLlmCallRepository = investigationLlmCallRepository;
		this.investigationBudgetService = investigationBudgetService;
		this.pricingProperties = pricingProperties;
	}

	@Transactional
	public void registrar(Long ticketId, AgenteLlm agente, LlmCallResult chamada) {
		BigDecimal custo = pricingProperties.estimarCustoUsd(chamada.model(), chamada.promptTokens(),
				chamada.completionTokens());

		InvestigationLlmCall linha = new InvestigationLlmCall();
		linha.setTicketId(ticketId);
		linha.setAgente(agente);
		linha.setModel(chamada.model());
		linha.setPromptTokens((int) chamada.promptTokens());
		linha.setCompletionTokens((int) chamada.completionTokens());
		linha.setTotalTokens((int) chamada.totalTokens());
		linha.setCustoEstimadoUsd(custo);
		linha.setCreatedAt(Instant.now());
		investigationLlmCallRepository.save(linha);

		investigationBudgetService.registrarConsumo(chamada.totalTokens(), custo);
	}
}
