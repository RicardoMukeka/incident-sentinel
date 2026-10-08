package com.ricardomukeka.incidentsentinel.config.properties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Preço por token, por modelo (docs/ARCHITECTURE.md) — a GCP/Vertex retorna
 * TOKENS, não dinheiro; o valor em US$ é sempre cálculo nosso, de config, com
 * snapshot no momento da chamada pra o custo histórico ficar estável quando o
 * preço do Gemini mudar.
 *
 * <p>{@code incidentsentinel.pricing.models.<model>.input-per-1m-usd} /
 * {@code .output-per-1m-usd} — a chave do map é o nome do modelo
 * ({@code spring.ai.google.genai.chat.model}, ex. {@code gemini-2.0-flash}).
 */
@ConfigurationProperties(prefix = "incidentsentinel.pricing")
public record PricingProperties(Map<String, ModelPrice> models) {

	public record ModelPrice(BigDecimal inputPer1mUsd, BigDecimal outputPer1mUsd) {
	}

	/**
	 * Custo estimado de uma chamada, em US$ com 6 casas. Modelo sem preço
	 * cadastrado → {@code ZERO} (não bloqueia a investigação; a ausência
	 * aparece como custo zero no rollup, sinal de config faltando).
	 */
	public BigDecimal estimarCustoUsd(String model, long promptTokens, long completionTokens) {
		ModelPrice price = models == null ? null : models.get(model);
		if (price == null) {
			return BigDecimal.ZERO.setScale(6, RoundingMode.HALF_UP);
		}
		BigDecimal umMilhao = BigDecimal.valueOf(1_000_000L);
		BigDecimal custoInput = price.inputPer1mUsd().multiply(BigDecimal.valueOf(promptTokens)).divide(umMilhao,
				10, RoundingMode.HALF_UP);
		BigDecimal custoOutput = price.outputPer1mUsd().multiply(BigDecimal.valueOf(completionTokens)).divide(umMilhao,
				10, RoundingMode.HALF_UP);
		return custoInput.add(custoOutput).setScale(6, RoundingMode.HALF_UP);
	}
}
