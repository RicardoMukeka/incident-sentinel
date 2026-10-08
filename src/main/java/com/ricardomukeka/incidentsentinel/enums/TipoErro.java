package com.ricardomukeka.incidentsentinel.enums;

/**
 * Os 5 tipos de erro padrão monitorados via SigNoz (docs/ARCHITECTURE.md).
 * Downstream e assíncrono usam um atributo extra na chave de dedup
 * (peer.service / tópico) — ver docs/ARCHITECTURE.md
 */
public enum TipoErro {
	HTTP_5XX,
	TIMEOUT,
	ERRO_DEPENDENCIA_DOWNSTREAM,
	LATENCIA_ANOMALA,
	FALHA_PROCESSAMENTO_ASSINCRONO
}
