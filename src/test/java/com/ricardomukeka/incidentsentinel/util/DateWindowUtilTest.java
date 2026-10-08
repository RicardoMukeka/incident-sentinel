package com.ricardomukeka.incidentsentinel.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class DateWindowUtilTest {

	private final Instant agora = Instant.parse("2026-08-20T12:00:00Z");
	private final Duration overlap = Duration.ofSeconds(30);
	private final Duration fallbackWindow = Duration.ofHours(1);
	private final Duration maxLookback = Duration.ofHours(24);

	@Test
	void semWatermark_usaFallback() {
		Instant desde = DateWindowUtil.calcularDesde(Optional.empty(), agora, overlap, fallbackWindow, maxLookback);

		assertThat(desde).isEqualTo(agora.minus(fallbackWindow));
	}

	@Test
	void comWatermarkRecente_subtraiOverlap() {
		Instant watermark = agora.minus(Duration.ofMinutes(5));

		Instant desde = DateWindowUtil.calcularDesde(Optional.of(watermark), agora, overlap, fallbackWindow,
				maxLookback);

		assertThat(desde).isEqualTo(watermark.minus(overlap));
	}

	@Test
	void comWatermarkAnomalamenteAntigo_aplicaTetoDeLookback() {
		Instant watermarkAntigo = agora.minus(Duration.ofDays(10));

		Instant desde = DateWindowUtil.calcularDesde(Optional.of(watermarkAntigo), agora, overlap, fallbackWindow,
				maxLookback);

		assertThat(desde).isEqualTo(agora.minus(maxLookback));
	}

	@Test
	void semWatermark_masFallbackMaiorQueLookback_aplicaTetoDeLookback() {
		Instant desde = DateWindowUtil.calcularDesde(Optional.empty(), agora, overlap, Duration.ofHours(48),
				maxLookback);

		assertThat(desde).isEqualTo(agora.minus(maxLookback));
	}
}
