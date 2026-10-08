package com.ricardomukeka.incidentsentinel.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * docs/ARCHITECTURE.md — margem de sobreposição, fallback de app+tipo novo e
 * teto defensivo de lookback usados pelo cálculo do watermark.
 */
@ConfigurationProperties(prefix = "incidentsentinel.collector")
public record CollectorProperties(
		String triggerSecret,
		Duration watermarkOverlap,
		Duration watermarkFallbackWindow,
		Duration watermarkMaxLookback,
		/**
		 * docs/ARCHITECTURE.md deixa esse threshold deliberadamente "a definir do zero,
		 * sem SLO prévio" — não há valor fechado na spec. Default aqui é só um
		 * ponto de partida, precisa ser calibrado contra a baseline real de
		 * service-orders/service-checkout em STG antes de ativar em produção.
		 */
		Duration latencyThreshold) {
}
