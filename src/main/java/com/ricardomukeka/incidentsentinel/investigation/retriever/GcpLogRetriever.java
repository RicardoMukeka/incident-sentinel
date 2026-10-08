package com.ricardomukeka.incidentsentinel.investigation.retriever;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.stereotype.Component;

import com.ricardomukeka.incidentsentinel.config.properties.GcpLoggingProperties;
import com.ricardomukeka.incidentsentinel.config.properties.InvestigationProperties;
import com.ricardomukeka.incidentsentinel.dto.LogFacts;
import com.ricardomukeka.incidentsentinel.exception.GcpLoggingException;
import com.ricardomukeka.incidentsentinel.util.ResilientCaller;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.Logging.EntryListOption;
import com.google.cloud.logging.Logging.SortingField;
import com.google.cloud.logging.Logging.SortingOrder;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;

/**
 * Retrieval das linhas de log da aplicação via GCP Cloud Logging (docs/ARCHITECTURE.md
 * §6.3/§8.3, docs/ARCHITECTURE.md) — lib {@code google-cloud-logging}, NÃO MCP.
 * Filtra por {@code trace} (todas as linhas de todos os serviços daquela
 * request, costuradas pelo trace_id) + {@code severity >= WARNING} + janela.
 *
 * <p>PENDÊNCIA (docs/ARCHITECTURE.md): pré-requisito é que os apps monitorados
 * propaguem trace context E escrevam o {@code trace_id} nas entradas do
 * Cloud Logging, e que esse valor case com o do SigNoz. Sem isso, cai pra
 * filtro por janela + severidade (mais ruído) — não implementado aqui,
 * decidir depois de validar contra STG. Multi-projeto: v1 usa o 1º projeto
 * configurado; se os apps logam em projetos diferentes, cada um precisa de
 * um {@code Logging} próprio (fora de escopo v1).
 */
@Component
public class GcpLogRetriever {

	private static final Logger log = LoggerFactory.getLogger(GcpLogRetriever.class);

	private final Logging logging;
	private final GcpLoggingProperties gcpLoggingProperties;
	private final InvestigationProperties investigationProperties;
	private final Resilience4JCircuitBreakerFactory circuitBreakerFactory;
	private final Retry retry;

	public GcpLogRetriever(Logging logging, GcpLoggingProperties gcpLoggingProperties,
			InvestigationProperties investigationProperties, Resilience4JCircuitBreakerFactory circuitBreakerFactory,
			RetryRegistry retryRegistry) {
		this.logging = logging;
		this.gcpLoggingProperties = gcpLoggingProperties;
		this.investigationProperties = investigationProperties;
		this.circuitBreakerFactory = circuitBreakerFactory;
		this.retry = retryRegistry.retry("gcp-logging");
	}

	public LogFacts buscar(String traceId, Instant ocorrenciaTs) {
		List<String> projetos = gcpLoggingProperties.projects();
		if (projetos == null || projetos.isEmpty()) {
			return LogFacts.vazio("nenhum projeto GCP configurado (incidentsentinel.gcp.logging.projects)");
		}
		String projeto = projetos.get(0);
		Instant desde = (ocorrenciaTs == null ? Instant.now() : ocorrenciaTs)
				.minus(investigationProperties.logWindow() == null ? Duration.ofMinutes(15)
						: investigationProperties.logWindow());

		String filtro = "trace=\"projects/%s/traces/%s\" AND severity>=WARNING AND timestamp>=\"%s\""
				.formatted(projeto, traceId, desde.toString());

		List<LogEntry> entradas = ResilientCaller.chamar("gcp-logging", circuitBreakerFactory, retry,
				() -> listar(filtro), throwable -> new GcpLoggingException("Falha ao consultar Cloud Logging", throwable));

		log.debug("Cloud Logging trace {} — {} entradas", traceId, entradas.size());
		return LogDistiller.destilar(entradas, investigationProperties.logMaxEntries());
	}

	private List<LogEntry> listar(String filtro) {
		List<LogEntry> acc = new ArrayList<>();
		int teto = Math.max(investigationProperties.logMaxEntries() * 3, 100);
		for (LogEntry e : logging.listLogEntries(
				EntryListOption.filter(filtro),
				EntryListOption.pageSize(Math.min(teto, 1000)),
				EntryListOption.sortOrder(SortingField.TIMESTAMP, SortingOrder.DESCENDING)).iterateAll()) {
			acc.add(e);
			if (acc.size() >= teto) {
				break;
			}
		}
		return acc;
	}
}
