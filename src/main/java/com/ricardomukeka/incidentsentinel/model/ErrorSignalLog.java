package com.ricardomukeka.incidentsentinel.model;

import java.time.Instant;

import com.ricardomukeka.incidentsentinel.enums.SinalOutcome;
import com.ricardomukeka.incidentsentinel.enums.TipoErro;

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
 * Um registro por sinal avaliado pela triagem, independente do desfecho —
 * fonte do watermark via MAX(timestamp) agrupado por app + tipo_erro
 * (docs/ARCHITECTURE.md). Escrita pela TriageService, na mesma transação do
 * ticket, depois que o sinal é avaliado com sucesso.
 */
@Entity
@Table(name = "error_signal_log", indexes = {
		@Index(name = "idx_error_signal_log_app_tipo_ts", columnList = "app, tipo_erro, timestamp")
})
public class ErrorSignalLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String app;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo_erro", nullable = false)
	private TipoErro tipoErro;

	@Column(name = "chave_dedup", length = 64, nullable = false)
	private String chaveDedup;

	@Column(name = "trace_id", length = 128)
	private String traceId;

	@Column(name = "timestamp", nullable = false)
	private Instant timestamp;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SinalOutcome outcome;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getApp() {
		return app;
	}

	public void setApp(String app) {
		this.app = app;
	}

	public TipoErro getTipoErro() {
		return tipoErro;
	}

	public void setTipoErro(TipoErro tipoErro) {
		this.tipoErro = tipoErro;
	}

	public String getChaveDedup() {
		return chaveDedup;
	}

	public void setChaveDedup(String chaveDedup) {
		this.chaveDedup = chaveDedup;
	}

	public String getTraceId() {
		return traceId;
	}

	public void setTraceId(String traceId) {
		this.traceId = traceId;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Instant timestamp) {
		this.timestamp = timestamp;
	}

	public SinalOutcome getOutcome() {
		return outcome;
	}

	public void setOutcome(SinalOutcome outcome) {
		this.outcome = outcome;
	}
}
