package com.ricardomukeka.incidentsentinel.investigation.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.retry.RetryRegistry;

/**
 * Mocka Resilience4JCircuitBreakerFactory/CircuitBreaker — o objetivo aqui é
 * verificar a lógica de wrapping (nome/parâmetros da tool), não o
 * comportamento do Resilience4j em si (biblioteca terceira já testada).
 */
@ExtendWith(MockitoExtension.class)
class TsuruDiagnoseAppServiceTest {

	@Mock
	private McpToolInvoker mcpToolInvoker;

	@Mock
	private Resilience4JCircuitBreakerFactory circuitBreakerFactory;

	@Mock
	private CircuitBreaker circuitBreaker;

	private TsuruDiagnoseAppService service;

	@BeforeEach
	void setUp() {
		when(circuitBreakerFactory.create("tsuru-mcp")).thenReturn(circuitBreaker);
		when(circuitBreaker.run(any(), any())).thenAnswer(inv -> {
			Supplier<?> supplier = inv.getArgument(0);
			return supplier.get();
		});
		service = new TsuruDiagnoseAppService(mcpToolInvoker, circuitBreakerFactory, RetryRegistry.ofDefaults());
	}

	@Test
	void diagnoseApp_chamaToolCorretaComParametroApp() {
		when(mcpToolInvoker.invocar(eq("diagnose_app"), anyMap()))
				.thenReturn(new ObjectMapper().createObjectNode().put("ok", true));

		var resultado = service.diagnoseApp("service-orders");

		assertThat(resultado.path("ok").asBoolean()).isTrue();
		verify(mcpToolInvoker).invocar("diagnose_app", Map.of("app", "service-orders"));
	}

	@Test
	void diagnoseRpaas_chamaToolCorreta() {
		when(mcpToolInvoker.invocar(eq("diagnose_rpaas"), anyMap())).thenReturn(new ObjectMapper().createObjectNode());

		service.diagnoseRpaas("service-checkout");

		verify(mcpToolInvoker).invocar("diagnose_rpaas", Map.of("app", "service-checkout"));
	}
}
