package com.ricardomukeka.incidentsentinel.dto.signoz;

/** Expressão de filtro na linguagem de query do SigNoz, ex.: "resource.service.name = 'x' AND has_error = true". */
public record SignozFilter(String expression) {
}
