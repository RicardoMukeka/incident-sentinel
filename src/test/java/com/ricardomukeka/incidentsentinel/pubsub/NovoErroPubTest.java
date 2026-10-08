package com.ricardomukeka.incidentsentinel.pubsub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Regressão do bug encontrado na revisão de arquitetura (2026-08-21): o
 * CompletableFuture devolvido por publish(...) era descartado, então uma
 * falha de publicação nunca era logada nem contabilizada.
 */
@ExtendWith(MockitoExtension.class)
class NovoErroPubTest {

	@Mock
	private PubSubTemplate pubSubTemplate;

	private SimpleMeterRegistry meterRegistry;
	private NovoErroPub novoErroPub;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		novoErroPub = new NovoErroPub(pubSubTemplate, meterRegistry);
		org.springframework.test.util.ReflectionTestUtils.setField(novoErroPub, "topicName", "novo-erro");
	}

	@Test
	void publicar_sucesso_naoIncrementaMetricaDeFalha() {
		when(pubSubTemplate.publish(anyString(), anyString())).thenReturn(CompletableFuture.completedFuture("msg-1"));

		novoErroPub.publicar(1L);

		assertThat(meterRegistry.find("incidentsentinel.pubsub.publish.failure").counter()).isNull();
	}

	@Test
	void publicar_falha_incrementaMetricaDeFalha() {
		CompletableFuture<String> futureComFalha = new CompletableFuture<>();
		futureComFalha.completeExceptionally(new RuntimeException("tópico inacessível"));
		when(pubSubTemplate.publish(anyString(), anyString())).thenReturn(futureComFalha);

		novoErroPub.publicar(2L);

		assertThat(meterRegistry.find("incidentsentinel.pubsub.publish.failure").counter().count()).isEqualTo(1.0);
	}
}
