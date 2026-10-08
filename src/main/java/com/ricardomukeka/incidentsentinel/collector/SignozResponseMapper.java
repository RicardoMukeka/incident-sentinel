package com.ricardomukeka.incidentsentinel.collector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.ricardomukeka.incidentsentinel.dto.ErrorSignalDto;
import com.ricardomukeka.incidentsentinel.enums.TipoErro;

/**
 * Extração defensiva do JSON de resposta de /api/v5/query_range. O SigNoz
 * não publica um schema de resposta documentado publicamente (confirmado ao
 * pesquisar em 2026-08-20 — só a documentação de request está disponível).
 * O shape assumido aqui ({@code data.result[].list[]} pra raw,
 * {@code data.result[].series[].values[]} pra time_series) é o padrão
 * historicamente conhecido das versões anteriores (v3/v4) da API do SigNoz
 * — <b>precisa ser confirmado contra uma resposta real de STG</b> antes de
 * confiar em produção (docs/ARCHITECTURE.md, verificação end-to-end, passo 3). Se o
 * shape divergir, os métodos abaixo devolvem lista/valor vazio em vez de
 * lançar exceção — falha silenciosa deliberada aqui, coberta pela métrica
 * incidentsentinel.triage.signal.failure quando a triagem existir (docs/ARCHITECTURE.md),
 * não pelo coletor.
 */
final class SignozResponseMapper {

	private SignozResponseMapper() {
	}

	static List<ErrorSignalDto> mapEventoDiscreto(JsonNode response, String app, TipoErro tipoErro,
			String atributoExtraFieldName, String traceIdFieldName) {
		List<ErrorSignalDto> sinais = new ArrayList<>();
		for (JsonNode resultado : response.path("data").path("result")) {
			for (JsonNode linha : resultado.path("list")) {
				Instant timestamp = extrairTimestamp(linha);
				if (timestamp == null) {
					continue;
				}
				String atributoExtra = atributoExtraFieldName == null ? null
						: extrairCampoTexto(linha, atributoExtraFieldName);
				String traceId = traceIdFieldName == null ? null : extrairCampoTexto(linha, traceIdFieldName);
				sinais.add(new ErrorSignalDto(app, tipoErro, timestamp, atributoExtra, traceId));
			}
		}
		return sinais;
	}

	static Optional<Double> mapUltimoValorAgregado(JsonNode response) {
		Double ultimoValor = null;
		for (JsonNode resultado : response.path("data").path("result")) {
			for (JsonNode serie : resultado.path("series")) {
				for (JsonNode ponto : serie.path("values")) {
					JsonNode valor = ponto.path("value");
					if (!valor.isMissingNode() && !valor.isNull()) {
						ultimoValor = valor.isTextual() ? Double.parseDouble(valor.asText()) : valor.asDouble();
					}
				}
			}
		}
		return Optional.ofNullable(ultimoValor);
	}

	// Limiares pra inferir a unidade de um epoch numérico pela ordem de grandeza (padrão comum
	// quando a unidade não é documentada — cada unidade é ~1000x a anterior, então uma data
	// "plausível", de ~1970 até ~2286, cai numa faixa sem ambiguidade entre unidades vizinhas).
	private static final long LIMITE_SEGUNDOS = 10_000_000_000L; // até aqui: epoch em segundos
	private static final long LIMITE_MILISSEGUNDOS = 10_000_000_000_000L; // até aqui: milissegundos
	private static final long LIMITE_MICROSSEGUNDOS = 10_000_000_000_000_000L; // até aqui: microssegundos
	// acima do último limite: nanossegundos

	private static Instant extrairTimestamp(JsonNode linha) {
		JsonNode ts = linha.has("timestamp") ? linha.get("timestamp") : linha.path("data").path("timestamp");
		if (ts.isMissingNode() || ts.isNull()) {
			return null;
		}
		try {
			return ts.isNumber() ? instantDeEpocaComUnidadeInferida(ts.asLong()) : Instant.parse(ts.asText());
		} catch (RuntimeException ex) {
			return null;
		}
	}

	/**
	 * Infere se um epoch numérico está em segundos, milissegundos, microssegundos ou
	 * nanossegundos pela ordem de grandeza, em vez de assumir milissegundos sempre (achado de
	 * code review 2026-08-22). O schema de resposta do SigNoz v5 não é documentado publicamente
	 * (ver javadoc da classe) e a mesma API expõe outros campos em nanossegundos (ex.:
	 * {@code duration_nano}) — sem essa inferência, um {@code timestamp} vindo em nanossegundos
	 * interpretado como milissegundos geraria uma data ~10^6 anos no futuro, avançando o
	 * watermark (docs/ARCHITECTURE.md) pra um valor que nunca mais seria alcançado por um ciclo
	 * seguinte — silencioso, sem exceção, sem métrica, só sinais que param de chegar.
	 */
	private static Instant instantDeEpocaComUnidadeInferida(long valor) {
		long magnitude = Math.abs(valor);
		if (magnitude < LIMITE_SEGUNDOS) {
			return Instant.ofEpochSecond(valor);
		}
		if (magnitude < LIMITE_MILISSEGUNDOS) {
			return Instant.ofEpochMilli(valor);
		}
		if (magnitude < LIMITE_MICROSSEGUNDOS) {
			return Instant.ofEpochSecond(valor / 1_000_000, (valor % 1_000_000) * 1_000);
		}
		return Instant.ofEpochSecond(valor / 1_000_000_000, valor % 1_000_000_000);
	}

	private static String extrairCampoTexto(JsonNode linha, String campo) {
		JsonNode valor = linha.has(campo) ? linha.get(campo) : linha.path("data").path(campo);
		return valor.isMissingNode() || valor.isNull() ? null : valor.asText();
	}
}
