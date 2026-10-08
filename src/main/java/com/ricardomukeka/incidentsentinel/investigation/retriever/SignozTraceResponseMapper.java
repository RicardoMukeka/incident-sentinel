package com.ricardomukeka.incidentsentinel.investigation.retriever;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Extração defensiva do JSON de {@code /api/v5/query_range} pro trace lookup
 * (docs/ARCHITECTURE.md). Assume o mesmo shape do mapper da coleta
 * ({@code data.result[].list[]}, cada item = um span) — schema v5 não
 * documentado publicamente, confirmar contra STG.
 *
 * <p>Dedup por {@code span_id}: o {@code query_range} via builder_query
 * retorna spans duplicados ([SigNoz #10449]). Sem dedup, a árvore ganharia
 * nós repetidos e a contagem de spans ficaria inflada.
 */
final class SignozTraceResponseMapper {

	private SignozTraceResponseMapper() {
	}

	static List<SpanRow> mapSpans(JsonNode response) {
		Map<String, SpanRow> porSpanId = new LinkedHashMap<>();
		if (response == null) {
			return List.of();
		}
		for (JsonNode resultado : response.path("data").path("result")) {
			for (JsonNode linha : resultado.path("list")) {
				SpanRow span = mapLinha(linha);
				if (span.spanId() != null) {
					porSpanId.putIfAbsent(span.spanId(), span);
				}
			}
		}
		return new ArrayList<>(porSpanId.values());
	}

	private static SpanRow mapLinha(JsonNode linha) {
		return new SpanRow(
				texto(linha, "span_id"),
				texto(linha, "parent_span_id"),
				texto(linha, "name"),
				primeiroNaoNulo(texto(linha, "resource.service.name"), texto(linha, "service.name")),
				texto(linha, "kind_string"),
				numeroLong(linha, "duration_nano"),
				Boolean.parseBoolean(String.valueOf(valor(linha, "has_error"))),
				texto(linha, "status_message"));
	}

	private static JsonNode valor(JsonNode linha, String campo) {
		JsonNode v = linha.has(campo) ? linha.get(campo) : linha.path("data").path(campo);
		return v.isMissingNode() ? null : v;
	}

	private static String texto(JsonNode linha, String campo) {
		JsonNode v = valor(linha, campo);
		return v == null || v.isNull() ? null : v.asText();
	}

	private static Long numeroLong(JsonNode linha, String campo) {
		JsonNode v = valor(linha, campo);
		if (v == null || v.isNull()) {
			return null;
		}
		try {
			return v.isNumber() ? v.asLong() : Long.parseLong(v.asText());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String primeiroNaoNulo(String a, String b) {
		return a != null ? a : b;
	}
}
