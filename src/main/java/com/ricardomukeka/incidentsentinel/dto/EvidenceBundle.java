package com.ricardomukeka.incidentsentinel.dto;

import java.util.Map;

/**
 * Evidência destilada das 3 fontes de retrieval (docs/ARCHITECTURE.md),
 * agregada pelo InvestigationOrchestrator antes da chamada de LLM. Os
 * {@code *Facts} nunca são {@code null} — quando uma fonte falha ou é pulada,
 * o retriever devolve um {@code *Facts.vazio(motivo)} e o desfecho fica em
 * {@code fontesConsultadas}.
 */
public record EvidenceBundle(
		TraceFacts trace,
		LogFacts logs,
		PlatformFacts platform,
		Map<String, String> fontesConsultadas) {
}
