package com.ricardomukeka.incidentsentinel.investigation.mcp;

import java.util.Map;

import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import io.github.resilience4j.retry.RetryRegistry;

/**
 * Tool primária da investigação (docs/ARCHITECTURE.md) — consulta barata e
 * determinística, chamada diretamente pelo InvestigationOrchestrator (passo
 * 8), não deixada a critério do LLM. Se o app tiver bind RPaaS, o {@code hint}
 * no retorno de {@code diagnose_app} exige follow-up com {@code diagnoseRpaas}
 * — honrar isso é responsabilidade do orquestrador (passo 8), não desta classe.
 *
 * <p><b>Nome do parâmetro "app" não confirmado.</b> Chute de melhor esforço
 * pela convenção do tsuru-cli (flags {@code --app}/{@code -a}) — o README do
 * tsuru-mcp não estava disponível nesta implementação. Confirmar via
 * {@code tools/list} contra um {@code tsuru-mcp --read-only} real antes de
 * integrar ao orquestrador (docs/ARCHITECTURE.md, verificação end-to-end).
 */
@Component
public class TsuruDiagnoseAppService extends AbstractMcpLookupService {

	public TsuruDiagnoseAppService(McpToolInvoker mcpToolInvoker,
			Resilience4JCircuitBreakerFactory circuitBreakerFactory, RetryRegistry retryRegistry) {
		super(mcpToolInvoker, circuitBreakerFactory, retryRegistry, "tsuru-mcp", "Tsuru");
	}

	public JsonNode diagnoseApp(String appName) {
		return chamarComResiliencia("diagnose_app", Map.of("app", appName));
	}

	public JsonNode diagnoseRpaas(String appName) {
		return chamarComResiliencia("diagnose_rpaas", Map.of("app", appName));
	}
}
