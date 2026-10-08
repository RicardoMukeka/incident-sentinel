package com.ricardomukeka.incidentsentinel.investigation.retriever;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ricardomukeka.incidentsentinel.dto.TraceFacts;

/**
 * Reduz a lista plana de spans (docs/ARCHITECTURE.md) ao que a análise de causa
 * raiz precisa — NÃO a árvore inteira. Mantém: o(s) span(s) de erro, a cadeia
 * de ancestrais até a raiz, os filhos imediatos do span de erro, e o span de
 * downstream que falhou (kind CLIENT com erro). Corta ao teto
 * {@code trace-max-spans} e formata como texto pro prompt.
 */
final class TraceDistiller {

	private TraceDistiller() {
	}

	static TraceFacts destilar(List<SpanRow> spans, int maxSpans) {
		if (spans.isEmpty()) {
			return TraceFacts.vazio("trace sem spans retornados");
		}

		Map<String, SpanRow> porId = new HashMap<>();
		Map<String, List<SpanRow>> filhosPorPai = new HashMap<>();
		for (SpanRow s : spans) {
			porId.put(s.spanId(), s);
			filhosPorPai.computeIfAbsent(s.parentSpanId() == null ? "" : s.parentSpanId(), k -> new ArrayList<>())
					.add(s);
		}

		List<SpanRow> comErro = spans.stream().filter(SpanRow::hasError).toList();
		SpanRow spanDeErro = comErro.isEmpty() ? spans.get(0) : comErro.get(comErro.size() - 1);

		Set<SpanRow> relevantes = new LinkedHashSet<>();
		// cadeia de ancestrais
		SpanRow atual = spanDeErro;
		int guarda = 0;
		while (atual != null && guarda++ < maxSpans) {
			relevantes.add(atual);
			atual = atual.parentSpanId() == null ? null : porId.get(atual.parentSpanId());
		}
		// filhos imediatos do span de erro
		relevantes.addAll(filhosPorPai.getOrDefault(spanDeErro.spanId(), List.of()));
		// downstream que falhou
		spans.stream().filter(s -> s.hasError() && "Client".equalsIgnoreCase(s.kind())).forEach(relevantes::add);

		List<SpanRow> topo = relevantes.stream().limit(maxSpans).toList();

		StringBuilder sb = new StringBuilder();
		sb.append("trace com ").append(spans.size()).append(" spans (mostrando ").append(topo.size())
				.append(" relevantes)\n");
		for (SpanRow s : topo) {
			sb.append("- ").append(s.servico() == null ? "?" : s.servico()).append(" | ")
					.append(s.name() == null ? "?" : s.name()).append(" | kind=").append(s.kind())
					.append(s.duracaoNano() == null ? "" : " | " + (s.duracaoNano() / 1_000_000) + "ms")
					.append(s.hasError() ? " | ERRO" : "")
					.append(s.statusMessage() == null || s.statusMessage().isBlank() ? ""
							: " | " + s.statusMessage())
					.append('\n');
		}

		String servicoFalhou = spanDeErro.servico();
		String tipoExcecao = spanDeErro.statusMessage();
		return new TraceFacts(sb.toString(), servicoFalhou, tipoExcecao);
	}
}
