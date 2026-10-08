package com.ricardomukeka.incidentsentinel.collector;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.ricardomukeka.incidentsentinel.client.signoz.SignozQueryClient;
import com.ricardomukeka.incidentsentinel.config.properties.CollectorProperties;
import com.ricardomukeka.incidentsentinel.dto.ErrorSignalDto;
import com.ricardomukeka.incidentsentinel.dto.signoz.QueryRangeRequest;
import com.ricardomukeka.incidentsentinel.enums.TipoErro;
import com.ricardomukeka.incidentsentinel.exception.SignozClientException;
import com.ricardomukeka.incidentsentinel.util.ResilientCaller;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Única implementação de ErrorSignalSource na v1 (docs/ARCHITECTURE.md).
 * Resiliência composta programaticamente — Retry(CircuitBreaker+TimeLimiter(chamada)) —
 * via os registries do Resilience4j, não por anotação (@CircuitBreaker/@Retry
 * exigiriam spring-boot-starter-aop pra tecelagem do @Aspect, e esse starter
 * não existe mais no Boot 4; docs/ARCHITECTURE.md). Instância "signoz" configurada
 * via as properties resilience4j.* em application.properties.
 */
@Component
public class SignozErrorSignalSource implements ErrorSignalSource {

	private static final Logger log = LoggerFactory.getLogger(SignozErrorSignalSource.class);

	private final SignozQueryClient signozQueryClient;
	private final CollectorProperties collectorProperties;
	private final Resilience4JCircuitBreakerFactory circuitBreakerFactory;
	private final Retry retry;
	private final MeterRegistry meterRegistry;

	public SignozErrorSignalSource(SignozQueryClient signozQueryClient, CollectorProperties collectorProperties,
			Resilience4JCircuitBreakerFactory circuitBreakerFactory, RetryRegistry retryRegistry,
			MeterRegistry meterRegistry) {
		this.signozQueryClient = signozQueryClient;
		this.collectorProperties = collectorProperties;
		this.circuitBreakerFactory = circuitBreakerFactory;
		this.retry = retryRegistry.retry("signoz");
		this.meterRegistry = meterRegistry;
	}

	@Override
	public List<ErrorSignalDto> fetchSignals(TipoErro tipoErro, String serviceName, Instant since) {
		Instant agora = Instant.now();
		QueryRangeRequest request = SignozQueryTemplateFactory.build(tipoErro, serviceName, since, agora);
		JsonNode response = chamarComResiliencia(request);

		if (tipoErro == TipoErro.LATENCIA_ANOMALA) {
			return SignozResponseMapper.mapUltimoValorAgregado(response)
					.filter(p99Nanos -> p99Nanos >= collectorProperties.latencyThreshold().toNanos())
					// timestamp = fim da janela consultada, nunca Instant.now() (docs/ARCHITECTURE.md).
					// traceId sempre null aqui: é uma agregação p99 sobre uma janela, sem span
					// causador único (docs/ARCHITECTURE.md) — não é uma lacuna, é estrutural.
					.map(p99Nanos -> new ErrorSignalDto(serviceName, tipoErro, agora, null, null))
					.map(List::of)
					.orElse(List.of());
		}

		String atributoExtraFieldName = SignozQueryTemplateFactory.atributoExtraFieldName(tipoErro);
		String traceIdFieldName = SignozQueryTemplateFactory.traceIdFieldName(tipoErro);
		List<ErrorSignalDto> sinais = SignozResponseMapper.mapEventoDiscreto(response, serviceName, tipoErro,
				atributoExtraFieldName, traceIdFieldName);
		avisarSePossivelmenteTruncado(sinais, serviceName, tipoErro);
		return sinais;
	}

	/**
	 * Achado de revisão (2026-08-21): o limite de {@code
	 * SignozQueryTemplateFactory.LIMITE_RESULTADOS_POR_CICLO} por ciclo não tem
	 * paginação — se a janela tiver mais eventos que o limite, os mais antigos
	 * somem, o watermark avança além deles, e eles nunca são buscados de novo.
	 * Implementar paginação de verdade exigiria confiar num mecanismo de cursor
	 * do SigNoz não confirmado (schema de resposta não documentado publicamente,
	 * ver SignozQueryTemplateFactory) — fora de escopo deste fix. O que dá pra
	 * fazer sem essa incerteza: tornar a suspeita de truncamento OBSERVÁVEL (log
	 * + métrica) em vez de silenciosa, pra alguém decidir se vale investigar.
	 * {@code >=} em vez de {@code ==} porque atingir exatamente o limite já é
	 * suspeito o bastante, não precisa provar que passou dele.
	 */
	private void avisarSePossivelmenteTruncado(List<ErrorSignalDto> sinais, String serviceName, TipoErro tipoErro) {
		if (sinais.size() >= SignozQueryTemplateFactory.LIMITE_RESULTADOS_POR_CICLO) {
			meterRegistry.counter("incidentsentinel.collector.signals.truncated", "app", serviceName, "tipo_erro",
					tipoErro.name()).increment();
			log.warn(
					"Possível truncamento: {} sinais retornados (limite {}) para {}/{} — sinais mais antigos da "
							+ "janela podem ter sido descartados e o watermark já avançou além deles",
					sinais.size(), SignozQueryTemplateFactory.LIMITE_RESULTADOS_POR_CICLO, serviceName, tipoErro);
		}
	}

	private JsonNode chamarComResiliencia(QueryRangeRequest request) {
		return ResilientCaller.chamar("signoz", circuitBreakerFactory, retry,
				() -> signozQueryClient.queryRange(request),
				throwable -> new SignozClientException("Falha ao consultar SigNoz query_range", throwable));
	}
}
