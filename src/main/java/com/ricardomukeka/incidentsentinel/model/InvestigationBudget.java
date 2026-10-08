package com.ricardomukeka.incidentsentinel.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Contador diário de chamadas ao ChatClient + custo real acumulado
 * (docs/ARCHITECTURE.md). O incremento é sempre via UPDATE atômico
 * (InvestigationBudgetRepository) — esta entidade não deve ser lida via
 * SELECT e regravada via save() para incrementar contador/tokens, sob
 * risco de perder incrementos concorrentes (2+ réplicas/threads).
 */
@Entity
@Table(name = "investigation_budget")
public class InvestigationBudget {

	@Id
	private LocalDate dia;

	private Long contador;

	@Column(name = "tokens_acumulados")
	private Long tokensAcumulados;

	@Column(name = "custo_acumulado_usd")
	private BigDecimal custoAcumuladoUsd;

	public LocalDate getDia() {
		return dia;
	}

	public void setDia(LocalDate dia) {
		this.dia = dia;
	}

	public Long getContador() {
		return contador;
	}

	public void setContador(Long contador) {
		this.contador = contador;
	}

	public Long getTokensAcumulados() {
		return tokensAcumulados;
	}

	public void setTokensAcumulados(Long tokensAcumulados) {
		this.tokensAcumulados = tokensAcumulados;
	}

	public BigDecimal getCustoAcumuladoUsd() {
		return custoAcumuladoUsd;
	}

	public void setCustoAcumuladoUsd(BigDecimal custoAcumuladoUsd) {
		this.custoAcumuladoUsd = custoAcumuladoUsd;
	}
}
