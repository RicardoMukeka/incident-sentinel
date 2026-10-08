package com.ricardomukeka.incidentsentinel.dto.signoz;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Payload de POST /api/v5/query_range (docs/ARCHITECTURE.md — versão confirmada
 * na implementação em 2026-08-20 via documentação oficial do SigNoz; a spec
 * citava "/api/v4/query_range ou equivalente", a versão real é v5).
 * start/end em epoch millis. Schema confirmado contra signoz.io/docs
 * (Trace API Payload Model) — ver SignozQueryTemplateFactory para os 5
 * templates e ressalvas de campos não documentados publicamente.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueryRangeRequest(long start, long end, String requestType, CompositeQuery compositeQuery) {
}
