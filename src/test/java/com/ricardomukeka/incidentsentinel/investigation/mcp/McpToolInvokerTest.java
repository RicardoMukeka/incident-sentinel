package com.ricardomukeka.incidentsentinel.investigation.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricardomukeka.incidentsentinel.exception.McpToolException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class McpToolInvokerTest {

	@Mock
	private ToolCallbackProvider toolCallbackProvider;

	@Mock
	private ToolCallback diagnoseAppCallback;

	@Mock
	private ToolDefinition diagnoseAppDefinition;

	private McpToolInvoker invoker;

	@BeforeEach
	void setUp() {
		invoker = new McpToolInvoker(toolCallbackProvider, new ObjectMapper(), new SimpleMeterRegistry());
	}

	@Test
	void invocar_encontraToolPeloNomeEChamaComJsonCorreto() {
		when(diagnoseAppDefinition.name()).thenReturn("diagnose_app");
		when(diagnoseAppCallback.getToolDefinition()).thenReturn(diagnoseAppDefinition);
		when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[] { diagnoseAppCallback });
		when(diagnoseAppCallback.call("{\"app\":\"service-orders\"}")).thenReturn("{\"problems\":[]}");

		JsonNode resultado = invoker.invocar("diagnose_app", Map.of("app", "service-orders"));

		assertThat(resultado.path("problems").isArray()).isTrue();
	}

	@Test
	void invocar_toolNaoEncontrada_lancaMcpToolException() {
		when(toolCallbackProvider.getToolCallbacks()).thenReturn(new ToolCallback[0]);

		assertThatThrownBy(() -> invoker.invocar("tool_inexistente", Map.of()))
				.isInstanceOf(McpToolException.class)
				.hasMessageContaining("tool_inexistente");
	}
}
