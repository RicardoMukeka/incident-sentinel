package com.ricardomukeka.incidentsentinel.model;

import java.time.Instant;

import com.ricardomukeka.incidentsentinel.enums.ExecucaoStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Mapeamento entre um app e o identificador que o representa na fonte de
 * sinal (hoje service.name no SigNoz). Cadastrar uma linha aqui é a única
 * ação necessária para um app entrar no monitoramento (docs/ARCHITECTURE.md).
 */
@Entity
@Table(name = "app_registry")
public class AppRegistry {

	@Id
	@Column(name = "service_name")
	private String serviceName;

	@Column(name = "display_name")
	private String displayName;

	private Boolean ativo;

	@Column(name = "criado_em")
	private Instant criadoEm;

	@Column(name = "ultima_execucao_inicio")
	private Instant ultimaExecucaoInicio;

	@Column(name = "ultima_execucao_fim")
	private Instant ultimaExecucaoFim;

	@Enumerated(EnumType.STRING)
	@Column(name = "ultima_execucao_status")
	private ExecucaoStatus ultimaExecucaoStatus;

	public String getServiceName() {
		return serviceName;
	}

	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public Boolean getAtivo() {
		return ativo;
	}

	public void setAtivo(Boolean ativo) {
		this.ativo = ativo;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	public void setCriadoEm(Instant criadoEm) {
		this.criadoEm = criadoEm;
	}

	public Instant getUltimaExecucaoInicio() {
		return ultimaExecucaoInicio;
	}

	public void setUltimaExecucaoInicio(Instant ultimaExecucaoInicio) {
		this.ultimaExecucaoInicio = ultimaExecucaoInicio;
	}

	public Instant getUltimaExecucaoFim() {
		return ultimaExecucaoFim;
	}

	public void setUltimaExecucaoFim(Instant ultimaExecucaoFim) {
		this.ultimaExecucaoFim = ultimaExecucaoFim;
	}

	public ExecucaoStatus getUltimaExecucaoStatus() {
		return ultimaExecucaoStatus;
	}

	public void setUltimaExecucaoStatus(ExecucaoStatus ultimaExecucaoStatus) {
		this.ultimaExecucaoStatus = ultimaExecucaoStatus;
	}
}
