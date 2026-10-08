package com.ricardomukeka.incidentsentinel.enums;

/**
 * Heartbeat de execução do coletor, gravado em app_registry no início e no
 * fim de cada ciclo (docs/ARCHITECTURE.md) — não é o watermark, só sinal
 * operacional para o endpoint GET /internal/collector/status.
 */
public enum ExecucaoStatus {
	EM_ANDAMENTO,
	SUCESSO,
	FALHA
}
