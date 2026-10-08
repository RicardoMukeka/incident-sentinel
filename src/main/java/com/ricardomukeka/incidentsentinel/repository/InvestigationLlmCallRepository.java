package com.ricardomukeka.incidentsentinel.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ricardomukeka.incidentsentinel.model.InvestigationLlmCall;

/** Detalhe por chamada de LLM (docs/ARCHITECTURE.md) — o rollup diário fica em investigation_budget. */
public interface InvestigationLlmCallRepository extends JpaRepository<InvestigationLlmCall, Long> {
}
