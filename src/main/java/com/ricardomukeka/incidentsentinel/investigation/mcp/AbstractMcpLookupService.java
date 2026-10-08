package com.ricardomukeka.incidentsentinel.investigation.mcp;

import java.util.Map;

import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.ricardomukeka.incidentsentinel.exception.McpToolException;
import com.ricardomukeka.incidentsentinel.util.ResilientCaller;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;

/**
 * Composição comum às chamadas MCP determinísticas (docs/ARCHITECTURE.md). Na v2
 * (docs/ARCHITECTURE.md) só o Tsuru ({@link TsuruDiagnoseAppService}) sobrou em MCP —
 * SigNoz virou REST e GCP virou client library — mas a base fica: se a Fase 2
 * (Prometheus) adicionar outra fonte MCP, ela reaproveita esta composição de
 * retry/circuit breaker em vez de replicar.
 *
 * <p>Cada subclasse só fornece o nome da instância de resiliência (mesmo nome
 * usado em {@code resilience4j.*.<nome>.*}) e o nome amigável da integração
 * pra mensagem de erro — a composição de retry/circuit breaker em si
 * ({@link ResilientCaller}) fica centralizada aqui.
 */
abstract class AbstractMcpLookupService {

	private final McpToolInvoker mcpToolInvoker;
	private final Resilience4JCircuitBreakerFactory circuitBreakerFactory;
	private final Retry retry;
	private final String nomeInstancia;
	private final String nomeAmigavel;

	protected AbstractMcpLookupService(McpToolInvoker mcpToolInvoker,
			Resilience4JCircuitBreakerFactory circuitBreakerFactory, RetryRegistry retryRegistry,
			String nomeInstancia, String nomeAmigavel) {
		this.mcpToolInvoker = mcpToolInvoker;
		this.circuitBreakerFactory = circuitBreakerFactory;
		this.retry = retryRegistry.retry(nomeInstancia);
		this.nomeInstancia = nomeInstancia;
		this.nomeAmigavel = nomeAmigavel;
	}

	protected JsonNode chamarComResiliencia(String tool, Map<String, Object> argumentos) {
		return ResilientCaller.chamar(nomeInstancia, circuitBreakerFactory, retry,
				() -> mcpToolInvoker.invocar(tool, argumentos), throwable -> new McpToolException(
						"Falha ao chamar MCP " + nomeAmigavel + " (" + tool + ")", throwable));
	}
}
