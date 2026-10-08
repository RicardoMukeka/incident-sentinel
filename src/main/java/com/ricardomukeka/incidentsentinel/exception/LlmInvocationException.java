package com.ricardomukeka.incidentsentinel.exception;

/**
 * Falha transitória / de infraestrutura na chamada ao modelo (Vertex AI) —
 * timeout, 429/503, circuit breaker aberto (docs/ARCHITECTURE.md). Diferente de
 * uma falha de parsing da saída estruturada: esta NÃO vira
 * {@code INVESTIGACAO_FALHOU_PARSING}; propaga pra o {@code NovoErroSub}
 * nack'ar a mensagem e deixar o Pub/Sub retentar com backoff (a guarda de
 * re-pagamento cobre o caso de uma tentativa anterior já ter pago).
 */
public class LlmInvocationException extends RuntimeException {

	public LlmInvocationException(String message, Throwable cause) {
		super(message, cause);
	}
}
