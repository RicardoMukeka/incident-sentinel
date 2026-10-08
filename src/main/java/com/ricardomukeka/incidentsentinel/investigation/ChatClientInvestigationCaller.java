package com.ricardomukeka.incidentsentinel.investigation;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.stereotype.Component;

import com.ricardomukeka.incidentsentinel.config.properties.InvestigationProperties;
import com.ricardomukeka.incidentsentinel.dto.CausaSolucaoResult;
import com.ricardomukeka.incidentsentinel.exception.LlmInvocationException;
import com.ricardomukeka.incidentsentinel.util.ResilientCaller;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;

/**
 * Isola a chamada ao ChatClient (docs/ARCHITECTURE.md):
 * {@code system} + {@code user} + structured output, <b>sem {@code .tools(...)}</b>.
 *
 * <p>Separação deliberada (docs/ARCHITECTURE.md): a <b>ida à rede</b> (round-trip
 * com o modelo) vai dentro do {@link ResilientCaller} — retry + circuit
 * breaker + timeout, instância {@code vertex-ai}; falha aí →
 * {@link LlmInvocationException} (infra, propaga pra nack/redelivery). A
 * <b>conversão</b> do texto pro record é feita <b>fora</b> do ResilientCaller
 * — falha aí é de parsing (RuntimeException normal), o orquestrador trata com
 * {@code INVESTIGACAO_FALHOU_PARSING}. Sem essa separação, um output
 * persistentemente malformado seria retentado via redelivery até a DLT.
 */
@Component
public class ChatClientInvestigationCaller {

	private final ChatClient chatClient;
	private final String model;
	private final Resilience4JCircuitBreakerFactory circuitBreakerFactory;
	private final Retry retry;
	private final InvestigationProperties investigationProperties;
	private final BeanOutputConverter<CausaSolucaoResult> converter =
			new BeanOutputConverter<>(CausaSolucaoResult.class);

	public ChatClientInvestigationCaller(ChatClient chatClient,
			@Value("${spring.ai.google.genai.chat.model:gemini-2.0-flash}") String model,
			Resilience4JCircuitBreakerFactory circuitBreakerFactory, RetryRegistry retryRegistry,
			InvestigationProperties investigationProperties) {
		this.chatClient = chatClient;
		this.model = model;
		this.circuitBreakerFactory = circuitBreakerFactory;
		this.retry = retryRegistry.retry("vertex-ai");
		this.investigationProperties = investigationProperties;
	}

	public LlmCallResult gerar(String systemPrompt, String userPrompt) {
		String userComFormato = userPrompt + "\n\n" + converter.getFormat();

		ChatResponse resposta = ResilientCaller.chamarComTimeout("vertex-ai", circuitBreakerFactory, retry,
				investigationProperties.llmTimeout(),
				() -> chatClient.prompt().system(systemPrompt).user(userComFormato).call().chatResponse(),
				throwable -> new LlmInvocationException("Falha ao chamar o modelo (Vertex AI)", throwable));

		String texto = resposta == null || resposta.getResult() == null ? null
				: resposta.getResult().getOutput().getText();
		CausaSolucaoResult resultado = converter.convert(texto); // falha aqui = parsing, tratada pelo orquestrador

		Usage usage = usageDe(resposta);
		return new LlmCallResult(resultado, model, valor(usage == null ? null : usage.getPromptTokens()),
				valor(usage == null ? null : usage.getCompletionTokens()),
				valor(usage == null ? null : usage.getTotalTokens()));
	}

	private Usage usageDe(ChatResponse response) {
		return response == null || response.getMetadata() == null ? null : response.getMetadata().getUsage();
	}

	private long valor(Number v) {
		return v == null ? 0L : v.longValue();
	}
}
