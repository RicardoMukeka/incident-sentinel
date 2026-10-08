package com.ricardomukeka.incidentsentinel.configuration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatModel injetado genericamente (não GoogleGenAiChatModel diretamente) —
 * quem decide a implementação concreta é a autoconfiguração do
 * spring-ai-starter-model-google-genai (docs/ARCHITECTURE.md).
 *
 * <p>Na v2 (docs/ARCHITECTURE.md) a investigação faz UMA chamada ao ChatClient SEM
 * tools — não há loop de tool-calling, então não é preciso configurar um
 * {@code ToolExecutionExceptionProcessor} (que existia na v1 pra desembrulhar
 * a exceção do teto de iterações).
 */
@Configuration
public class ChatClientConfig {

	@Bean
	public ChatClient investigationChatClient(ChatModel chatModel) {
		return ChatClient.builder(chatModel).build();
	}
}
