package com.ricardomukeka.incidentsentinel.util;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;

import io.github.resilience4j.retry.Retry;

/**
 * Composição programática Retry(CircuitBreaker(chamada)) — não anotação
 * {@code @Retry}/{@code @CircuitBreaker} (docs/ARCHITECTURE.md — Boot 4 não tem
 * {@code spring-boot-starter-aop}). Usado por {@code SignozErrorSignalSource},
 * {@code SignozTraceRetriever}, {@code GcpLogRetriever},
 * {@code TsuruDiagnoseAppService} e {@code ChatClientInvestigationCaller}.
 *
 * <p>{@link #chamarComTimeout} adiciona um timeout de verdade — o
 * {@code resilience4j.timelimiter.*} não é aplicado por esta classe, então
 * pra Vertex AI (sem Feign, sem deadline nosso) esse timeout fecha o gap de
 * uma chamada que trava indefinidamente (docs/ARCHITECTURE.md).
 */
public final class ResilientCaller {

	private ResilientCaller() {
	}

	public static <T> T chamar(String nomeInstancia, Resilience4JCircuitBreakerFactory circuitBreakerFactory,
			Retry retry, Supplier<T> chamada, Function<Throwable, ? extends RuntimeException> excecaoDeFalha) {
		Supplier<T> viaCircuitBreaker = () -> {
			CircuitBreaker circuitBreaker = circuitBreakerFactory.create(nomeInstancia);
			Function<Throwable, T> fallback = throwable -> {
				throw excecaoDeFalha.apply(throwable);
			};
			return circuitBreaker.run(chamada, fallback);
		};
		return retry.executeSupplier(viaCircuitBreaker);
	}

	/** Igual a {@link #chamar}, mas com timeout na chamada (executada em virtual thread). */
	public static <T> T chamarComTimeout(String nomeInstancia, Resilience4JCircuitBreakerFactory circuitBreakerFactory,
			Retry retry, Duration timeout, Supplier<T> chamada,
			Function<Throwable, ? extends RuntimeException> excecaoDeFalha) {
		Supplier<T> comTimeout = () -> {
			try {
				return CompletableFuture.supplyAsync(chamada).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
			} catch (CompletionException e) {
				throw e.getCause() instanceof RuntimeException re ? re : new CompletionException(e.getCause());
			} catch (java.util.concurrent.ExecutionException e) {
				throw e.getCause() instanceof RuntimeException re ? re : new IllegalStateException(e.getCause());
			} catch (java.util.concurrent.TimeoutException e) {
				throw new IllegalStateException("timeout após " + timeout, e);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("interrompido", e);
			}
		};
		return chamar(nomeInstancia, circuitBreakerFactory, retry, comTimeout, excecaoDeFalha);
	}
}
