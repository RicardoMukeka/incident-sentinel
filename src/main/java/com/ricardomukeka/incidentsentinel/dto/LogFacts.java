package com.ricardomukeka.incidentsentinel.dto;

/**
 * Saída destilada do GcpLogRetriever (docs/ARCHITECTURE.md) — as linhas
 * severity &gt;= WARNING do Cloud Logging correlacionadas ao trace, cortadas
 * ao teto (incidentsentinel.investigation.log-max-entries) e formatadas como texto
 * pro prompt: {@code [timestamp] SEVERITY servico: mensagem/exceção}.
 */
public record LogFacts(String resumo, int totalLinhas) {

	public static LogFacts vazio(String motivo) {
		return new LogFacts(motivo, 0);
	}
}
