package com.ricardomukeka.incidentsentinel.dto;

import com.ricardomukeka.incidentsentinel.enums.CausaCategoria;
import com.ricardomukeka.incidentsentinel.enums.ConfiancaDiagnostico;

/**
 * Structured output da única chamada de LLM da investigação (docs/ARCHITECTURE.md
 * §6.4). O LLM não recebe nenhuma tool — só a evidência destilada no prompt,
 * e devolve este shape.
 *
 * <p>{@code solucaoProposta} vem vazia quando {@code confiancaCausa} é BAIXA
 * (instrução no system prompt). Valores de enum nulos/não reconhecidos caem
 * em fallback na camada que consome (TicketGenerationService), não aqui.
 */
public record CausaSolucaoResult(
		String causaRaiz,
		CausaCategoria causaCategoria,
		ConfiancaDiagnostico confiancaCausa,
		String solucaoProposta,
		ConfiancaDiagnostico confiancaSolucao) {
}
