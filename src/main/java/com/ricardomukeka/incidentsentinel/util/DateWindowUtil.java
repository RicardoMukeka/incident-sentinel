package com.ricardomukeka.incidentsentinel.util;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Cálculo de "desde" pro coletor (docs/ARCHITECTURE.md). O watermark existe pra
 * controlar volume/eficiência da busca no SigNoz, não pra garantir "busquei
 * cada erro exatamente uma vez" — quem garante isso é a chave de dedup
 * (docs/ARCHITECTURE.md). Fórmula: desde = max(watermark - overlap, agora - maxLookback);
 * sem watermark (app+tipo novo): desde = agora - fallbackWindow.
 */
public final class DateWindowUtil {

	private DateWindowUtil() {
	}

	public static Instant calcularDesde(Optional<Instant> watermark, Instant agora, Duration overlap,
			Duration fallbackWindow, Duration maxLookback) {
		Instant tetoLookback = agora.minus(maxLookback);

		if (watermark.isEmpty()) {
			Instant desdeFallback = agora.minus(fallbackWindow);
			return desdeFallback.isBefore(tetoLookback) ? tetoLookback : desdeFallback;
		}

		Instant desdeWatermark = watermark.get().minus(overlap);
		return desdeWatermark.isBefore(tetoLookback) ? tetoLookback : desdeWatermark;
	}
}
