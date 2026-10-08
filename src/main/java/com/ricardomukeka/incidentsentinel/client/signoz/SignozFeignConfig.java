package com.ricardomukeka.incidentsentinel.client.signoz;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;

import com.ricardomukeka.incidentsentinel.config.properties.SignozProperties;

import feign.Request;
import feign.RequestInterceptor;

/**
 * Header de autenticação confirmado na implementação: SIGNOZ-API-KEY
 * (docs.spring.io não se aplica aqui — confirmado direto na doc oficial do
 * SigNoz, docs/ARCHITECTURE.md).
 *
 * <p>Timeout de connect/read via Feign Request.Options — é o timeout de verdade
 * do SigNoz (o resilience4j.timelimiter.* não é aplicado, docs/ARCHITECTURE.md).
 * Retry + circuit breaker ficam em SignozErrorSignalSource/SignozTraceRetriever, composto
 * programaticamente contra os registries do Resilience4j, não via anotação
 * (spring-boot-starter-aop não existe mais no Boot 4 — ver docs/ARCHITECTURE.md).
 */
public class SignozFeignConfig {

	@Bean
	public RequestInterceptor signozApiKeyInterceptor(SignozProperties signozProperties) {
		return template -> template.header("SIGNOZ-API-KEY", signozProperties.token());
	}

	@Bean
	public Request.Options signozRequestOptions() {
		return new Request.Options(5, TimeUnit.SECONDS, 15, TimeUnit.SECONDS, true);
	}
}
