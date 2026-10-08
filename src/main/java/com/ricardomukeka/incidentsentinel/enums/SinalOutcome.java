package com.ricardomukeka.incidentsentinel.enums;

/**
 * Desfecho do sinal avaliado pela triagem, gravado em error_signal_log
 * (docs/ARCHITECTURE.md) — distingue "criei tíquete novo" de "só associei
 * ocorrência a um tíquete já existente hoje".
 */
public enum SinalOutcome {
	NOVO_TICKET,
	ASSOCIADO
}
