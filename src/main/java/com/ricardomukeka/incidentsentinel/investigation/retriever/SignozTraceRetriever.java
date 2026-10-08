package com.ricardomukeka.incidentsentinel.investigation.retriever;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.ricardomukeka.incidentsentinel.client.signoz.SignozQueryClient;
import com.ricardomukeka.incidentsentinel.collector.SignozQueryTemplateFactory;
import com.ricardomukeka.incidentsentinel.config.properties.InvestigationProperties;
import com.ricardomukeka.incidentsentinel.dto.TraceFacts;
import com.ricardomukeka.incidentsentinel.dto.signoz.QueryRangeRequest;
import com.ricardomukeka.incidentsentinel.exception.SignozClientException;
import com.ricardomukeka.incidentsentinel.util.ResilientCaller;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;

/**
 * Retrieval do trace de execução via SigNoz REST (docs/ARCHITECTURE.md)
 * — NÃO MCP. Mesmo {@code /api/v5/query_range} da coleta, filtro {@code trace_id},
 * + dedup de span ({@link SignozTraceResponseMapper}) e destilação
 * ({@link TraceDistiller}) que o wrapper MCP não fazia.
 *
 * <p>Chamado deterministicamente pelo InvestigationOrchestrator, em paralelo
 * com os outros retrievers. Instância de resiliência {@code signoz} (a mesma
 * da coleta — é o mesmo endpoint).
 */
@Component
public class SignozTraceRetriever {

	private static final Logger log = LoggerFactory.getLogger(SignozTraceRetriever.class);
	private static final Duration JANELA_BUSCA = Duration.ofHours(6);

	private final SignozQueryClient signozQueryClient;
	private final InvestigationProperties investigationProperties;
	private final Resilience4JCircuitBreakerFactory circuitBreakerFactory;
	private final Retry retry;

	public SignozTraceRetriever(SignozQueryClient signozQueryClient, InvestigationProperties investigationProperties,
			Resilience4JCircuitBreakerFactory circuitBreakerFactory, RetryRegistry retryRegistry) {
		this.signozQueryClient = signozQueryClient;
		this.investigationProperties = investigationProperties;
		this.circuitBreakerFactory = circuitBreakerFactory;
		this.retry = retryRegistry.retry("signoz");
	}

	/**
	 * @param ocorrenciaTs timestamp da ocorrência que abriu o ticket — a janela
	 *                     de busca do trace é centrada nele; {@code null} cai
	 *                     numa janela terminando agora.
	 */
	public TraceFacts buscar(String traceId, Instant ocorrenciaTs) {
		Instant fim = ocorrenciaTs == null ? Instant.now() : ocorrenciaTs.plus(Duration.ofMinutes(5));
		Instant inicio = fim.minus(JANELA_BUSCA);
		QueryRangeRequest request = SignozQueryTemplateFactory.buildTraceById(traceId, inicio, fim);

		JsonNode response = ResilientCaller.chamar("signoz", circuitBreakerFactory, retry,
				() -> signozQueryClient.queryRange(request),
				throwable -> new SignozClientException("Falha ao buscar trace no SigNoz", throwable));

		List<SpanRow> spans = SignozTraceResponseMapper.mapSpans(response);
		log.debug("Trace {} — {} spans após dedup", traceId, spans.size());
		return TraceDistiller.destilar(spans, investigationProperties.traceMaxSpans());
	}
}
