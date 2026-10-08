package com.ricardomukeka.incidentsentinel.investigation.mcp;

import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricardomukeka.incidentsentinel.exception.McpToolException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Invocação programática de uma tool MCP pelo nome — na v2 (docs/ARCHITECTURE.md) o
 * único MCP é o Tsuru ({@code diagnose_app}/{@code diagnose_rpaas}), chamado
 * deterministicamente pelo retrieval (docs/ARCHITECTURE.md), nunca decidido pelo
 * LLM (o ChatClient não recebe tool nenhuma). O servidor Tsuru roda com
 * {@code READ_ONLY=true}, então tools de escrita nem são registradas.
 *
 * <p>Emite {@code incidentsentinel.investigation.tool.call} (Counter) e
 * {@code incidentsentinel.investigation.tool.call.duration} (Timer), tags
 * {@code tool}/{@code mcp}/{@code outcome}.
 */
@Component
public class McpToolInvoker {

	private static final Logger log = LoggerFactory.getLogger(McpToolInvoker.class);

	private final ToolCallbackProvider toolCallbackProvider;
	private final ObjectMapper objectMapper;
	private final MeterRegistry meterRegistry;

	public McpToolInvoker(ToolCallbackProvider toolCallbackProvider, ObjectMapper objectMapper,
			MeterRegistry meterRegistry) {
		this.toolCallbackProvider = toolCallbackProvider;
		this.objectMapper = objectMapper;
		this.meterRegistry = meterRegistry;
	}

	public JsonNode invocar(String nomeDaTool, Map<String, Object> argumentos) {
		ToolCallback callback = Arrays.stream(toolCallbackProvider.getToolCallbacks())
				.filter(tc -> tc.getToolDefinition().name().equals(nomeDaTool))
				.findFirst()
				.orElseThrow(() -> new McpToolException(
						"Tool MCP não encontrada (fora da allow-list ou servidor indisponível): " + nomeDaTool));

		String mcp = mcpDaTool(nomeDaTool);
		Timer.Sample sample = Timer.start(meterRegistry);
		String outcome = "falha";
		try {
			String argumentosJson = objectMapper.writeValueAsString(argumentos);
			String resultadoJson = callback.call(argumentosJson);
			outcome = "sucesso";
			return objectMapper.readTree(resultadoJson);
		} catch (JsonProcessingException e) {
			throw new McpToolException("Falha ao (des)serializar chamada MCP: " + nomeDaTool, e);
		} finally {
			sample.stop(meterRegistry.timer("incidentsentinel.investigation.tool.call.duration", "tool", nomeDaTool, "mcp",
					mcp));
			meterRegistry.counter("incidentsentinel.investigation.tool.call", "tool", nomeDaTool, "mcp", mcp, "outcome",
					outcome).increment();
			log.info("MCP tool call: tool={} mcp={} outcome={}", nomeDaTool, mcp, outcome);
		}
	}

	private static String mcpDaTool(String nomeDaTool) {
		return switch (nomeDaTool) {
			case "diagnose_app", "diagnose_rpaas" -> "tsuru";
			default -> "desconhecido";
		};
	}
}
