package com.ricardomukeka.incidentsentinel.investigation.retriever;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.ricardomukeka.incidentsentinel.dto.PlatformFacts;
import com.ricardomukeka.incidentsentinel.investigation.mcp.TsuruDiagnoseAppService;

/**
 * Retrieval do diagnóstico de plataforma via MCP Tsuru (docs/ARCHITECTURE.md,
 * docs/ARCHITECTURE.md) — o ÚNICO MCP do sistema, porque {@code diagnose_app} é
 * agregação/classificação real (21 categorias), não wrapper fino.
 *
 * <p>{@code diagnose_app} já devolve os {@code problems} tipados e
 * priorizados, então a "destilação" aqui é mínima — só serializa o JSON pro
 * texto do prompt. Se o retorno indicar bind RPaaS (campo {@code hint}),
 * faz o follow-up com {@code diagnose_rpaas}.
 */
@Component
public class TsuruDiagnoseRetriever {

	private final TsuruDiagnoseAppService tsuruDiagnoseAppService;

	public TsuruDiagnoseRetriever(TsuruDiagnoseAppService tsuruDiagnoseAppService) {
		this.tsuruDiagnoseAppService = tsuruDiagnoseAppService;
	}

	public PlatformFacts buscar(String app) {
		JsonNode diagnostico = tsuruDiagnoseAppService.diagnoseApp(app);
		StringBuilder sb = new StringBuilder(diagnostico == null ? "diagnose_app sem retorno" : diagnostico.toString());

		if (diagnostico != null && exigeRpaas(diagnostico)) {
			JsonNode rpaas = tsuruDiagnoseAppService.diagnoseRpaas(app);
			sb.append("\ndiagnose_rpaas: ").append(rpaas == null ? "sem retorno" : rpaas.toString());
		}
		return new PlatformFacts(sb.toString());
	}

	private boolean exigeRpaas(JsonNode diagnostico) {
		JsonNode hint = diagnostico.path("hint");
		return hint.isTextual() && hint.asText().toLowerCase().contains("rpaas");
	}
}
