package com.ricardomukeka.incidentsentinel.investigation.retriever;

import java.util.List;

import com.ricardomukeka.incidentsentinel.dto.LogFacts;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Payload;
import com.google.cloud.logging.Severity;

/**
 * Corta e formata as entradas do Cloud Logging (docs/ARCHITECTURE.md) pro
 * prompt: {@code [timestamp] SEVERITY servico: mensagem}. Ordena por
 * timestamp asc (narrativa) e limita ao teto {@code log-max-entries}.
 */
final class LogDistiller {

	private static final int MAX_MENSAGEM_CHARS = 800;

	private LogDistiller() {
	}

	static LogFacts destilar(List<LogEntry> entradas, int maxEntradas) {
		if (entradas.isEmpty()) {
			return LogFacts.vazio("nenhuma linha de log severity>=WARNING correlacionada ao trace");
		}

		List<LogEntry> ordenadas = entradas.stream()
				.sorted((a, b) -> Long.compare(nvl(a.getTimestamp()), nvl(b.getTimestamp())))
				.limit(maxEntradas)
				.toList();

		StringBuilder sb = new StringBuilder();
		sb.append(entradas.size()).append(" linhas (mostrando ").append(ordenadas.size()).append(")\n");
		for (LogEntry e : ordenadas) {
			sb.append('[').append(e.getTimestamp()).append("] ")
					.append(e.getSeverity() == null ? Severity.DEFAULT : e.getSeverity()).append(' ')
					.append(servico(e)).append(": ").append(mensagem(e)).append('\n');
		}
		return new LogFacts(sb.toString(), entradas.size());
	}

	private static long nvl(Long v) {
		return v == null ? 0L : v;
	}

	private static String servico(LogEntry e) {
		Object svc = e.getResource() == null ? null : e.getResource().getLabels().get("service_name");
		if (svc == null && e.getResource() != null) {
			svc = e.getResource().getLabels().get("container_name");
		}
		return svc == null ? "?" : svc.toString();
	}

	private static String mensagem(LogEntry e) {
		String texto = switch (e.getPayload().getType()) {
			case STRING -> e.<Payload.StringPayload>getPayload().getData();
			case JSON -> e.<Payload.JsonPayload>getPayload().getDataAsMap().toString();
			case PROTO -> String.valueOf(e.getPayload().getData());
		};
		if (texto == null) {
			return "";
		}
		return texto.length() > MAX_MENSAGEM_CHARS ? texto.substring(0, MAX_MENSAGEM_CHARS) + "…" : texto;
	}
}
