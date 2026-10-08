package com.ricardomukeka.incidentsentinel.dto;

/**
 * Saída destilada do SignozTraceRetriever (docs/ARCHITECTURE.md) — NÃO a árvore
 * de spans crua. O {@code resumo} já vem cortado ao teto de spans
 * (incidentsentinel.investigation.trace-max-spans) e formatado como texto pra
 * entrar direto no prompt: span de erro + cadeia de ancestrais + filhos
 * imediatos + downstream que falhou + timings + eventos de exceção.
 *
 * <p>{@code servicoQueFalhou} e {@code tipoExcecao} são extraídos quando
 * identificáveis (podem ser {@code null}) — dão ao orquestrador/prompt um
 * gancho estruturado sem precisar reparsear o texto.
 */
public record TraceFacts(String resumo, String servicoQueFalhou, String tipoExcecao) {

	public static TraceFacts vazio(String motivo) {
		return new TraceFacts(motivo, null, null);
	}
}
