package com.ricardomukeka.incidentsentinel.dto;

/**
 * Saída do TsuruDiagnoseRetriever (docs/ARCHITECTURE.md) — a lista {@code problems}
 * tipada e priorizada do {@code diagnose_app} (21 categorias), como texto. Já
 * vem destilada pelo próprio servidor MCP do Tsuru, então o {@code resumo} é
 * o JSON/texto do diagnóstico com pouco ou nenhum corte adicional.
 */
public record PlatformFacts(String resumo) {

	public static PlatformFacts vazio(String motivo) {
		return new PlatformFacts(motivo);
	}
}
