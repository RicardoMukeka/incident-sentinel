package com.ricardomukeka.incidentsentinel.model;

import java.math.BigDecimal;
import java.time.Instant;

import com.ricardomukeka.incidentsentinel.enums.AgenteLlm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Uma linha por chamada de LLM numa investigação (docs/ARCHITECTURE.md) — 1
 * por investigação na v1, 2 na v2. Granularidade por chamada pra responder
 * "qual etapa custa mais" e pra o custo histórico ficar estável.
 *
 * <p>{@code custo_estimado_usd} é {@code tokens × preço-da-época} de um price
 * map em config (PricingProperties) — snapshot no momento da chamada. A
 * GCP/Vertex retorna tokens, não dinheiro.
 */
@Entity
@Table(name = "investigation_llm_call", indexes = {
		@Index(name = "idx_llm_call_ticket", columnList = "ticket_id"),
		@Index(name = "idx_llm_call_created_at", columnList = "created_at")
})
public class InvestigationLlmCall {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "ticket_id", nullable = false)
	private Long ticketId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AgenteLlm agente;

	@Column(length = 60)
	private String model;

	@Column(name = "prompt_tokens")
	private Integer promptTokens;

	@Column(name = "completion_tokens")
	private Integer completionTokens;

	@Column(name = "total_tokens")
	private Integer totalTokens;

	@Column(name = "custo_estimado_usd")
	private BigDecimal custoEstimadoUsd;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getTicketId() {
		return ticketId;
	}

	public void setTicketId(Long ticketId) {
		this.ticketId = ticketId;
	}

	public AgenteLlm getAgente() {
		return agente;
	}

	public void setAgente(AgenteLlm agente) {
		this.agente = agente;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public Integer getPromptTokens() {
		return promptTokens;
	}

	public void setPromptTokens(Integer promptTokens) {
		this.promptTokens = promptTokens;
	}

	public Integer getCompletionTokens() {
		return completionTokens;
	}

	public void setCompletionTokens(Integer completionTokens) {
		this.completionTokens = completionTokens;
	}

	public Integer getTotalTokens() {
		return totalTokens;
	}

	public void setTotalTokens(Integer totalTokens) {
		this.totalTokens = totalTokens;
	}

	public BigDecimal getCustoEstimadoUsd() {
		return custoEstimadoUsd;
	}

	public void setCustoEstimadoUsd(BigDecimal custoEstimadoUsd) {
		this.custoEstimadoUsd = custoEstimadoUsd;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
