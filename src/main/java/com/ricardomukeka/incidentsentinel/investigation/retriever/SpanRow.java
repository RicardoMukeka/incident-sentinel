package com.ricardomukeka.incidentsentinel.investigation.retriever;

/**
 * Uma linha de span da resposta do trace lookup (docs/ARCHITECTURE.md), já
 * deduplicada por {@code spanId}. Shape defensivo — qualquer campo pode ser
 * {@code null} se o SigNoz não devolver (schema v5 não documentado
 * publicamente, ver SignozQueryTemplateFactory).
 */
public record SpanRow(
		String spanId,
		String parentSpanId,
		String name,
		String servico,
		String kind,
		Long duracaoNano,
		boolean hasError,
		String statusMessage) {
}
