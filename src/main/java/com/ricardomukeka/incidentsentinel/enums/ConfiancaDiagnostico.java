package com.ricardomukeka.incidentsentinel.enums;

/**
 * Confiança do diagnóstico gerado pelo investigador (docs/ARCHITECTURE.md).
 * Valor de enum não reconhecido no structured output do LLM cai em
 * fallback BAIXA, não falha dura — tratado na camada de parsing, não aqui.
 */
public enum ConfiancaDiagnostico {
	ALTA,
	MEDIA,
	BAIXA
}
