package com.ricardomukeka.incidentsentinel.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.ricardomukeka.incidentsentinel.enums.ConfiancaDiagnostico;
import com.ricardomukeka.incidentsentinel.enums.TicketStatus;
import com.ricardomukeka.incidentsentinel.enums.TipoErro;
import com.ricardomukeka.incidentsentinel.model.Ticket;

/** GET /tickets (docs/ARCHITECTURE.md) — sem chave_dedup/fontes_consultadas/causa_raiz, só o essencial pra listagem. */
public record TicketSummaryDto(Long id, String app, TipoErro tipoErro, LocalDate data, TicketStatus status,
		ConfiancaDiagnostico confiancaCausa, ConfiancaDiagnostico confiancaSolucao, Integer contagemOcorrencias,
		Instant primeiraOcorrenciaTs, Instant ultimaOcorrenciaTs) {

	public static TicketSummaryDto from(Ticket ticket) {
		return new TicketSummaryDto(ticket.getId(), ticket.getApp(), ticket.getTipoErro(), ticket.getData(),
				ticket.getStatus(), ticket.getConfiancaCausa(), ticket.getConfiancaSolucao(),
				ticket.getContagemOcorrencias(), ticket.getPrimeiraOcorrenciaTs(), ticket.getUltimaOcorrenciaTs());
	}
}
