package com.ricardomukeka.incidentsentinel.dto;

import java.time.Instant;

import com.ricardomukeka.incidentsentinel.enums.TipoErro;

/**
 * Formato comum passado do coletor pra triagem, em memória (docs/ARCHITECTURE.md,
 * pacote collector/). {@code atributoExtra} é o peer.service (downstream)
 * ou tópico/DLT (assíncrono) usado na chave de dedup (docs/ARCHITECTURE.md) —
 * null para os tipos que usam dedup simples (5xx, timeout, latência).
 * {@code timestamp} é o timestamp do evento, ou o fim da janela consultada
 * pro tipo LATENCIA_ANOMALA (docs/ARCHITECTURE.md). {@code traceId} é usado
 * exclusivamente pela investigação (SignozTraceRetriever, docs/ARCHITECTURE.md
 * §6.3) pra buscar o trace detalhado depois — sempre null pra
 * LATENCIA_ANOMALA (agregação p99, sem span causador único).
 */
public record ErrorSignalDto(String app, TipoErro tipoErro, Instant timestamp, String atributoExtra, String traceId) {
}
