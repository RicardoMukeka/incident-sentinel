package com.ricardomukeka.incidentsentinel.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.ricardomukeka.incidentsentinel.enums.AvaliacaoCausa;
import com.ricardomukeka.incidentsentinel.enums.AvaliacaoSolucao;
import com.ricardomukeka.incidentsentinel.enums.CausaCategoria;
import com.ricardomukeka.incidentsentinel.enums.ConfiancaDiagnostico;
import com.ricardomukeka.incidentsentinel.enums.TicketStatus;
import com.ricardomukeka.incidentsentinel.enums.TipoErro;
import com.ricardomukeka.incidentsentinel.model.Ticket;

/** GET /tickets/{id} (docs/ARCHITECTURE.md) — detalhe completo: saída da investigação + registro de resolução. */
public record TicketDetailDto(
		Long id, String app, TipoErro tipoErro, String chaveDedup, LocalDate data, String traceId,
		TicketStatus status,
		String causaRaiz, CausaCategoria causaCategoria, ConfiancaDiagnostico confiancaCausa,
		String solucaoProposta, ConfiancaDiagnostico confiancaSolucao, Instant investigadoEm,
		String fontesConsultadas,
		Instant resolvidoEm, String resolvidoPor,
		AvaliacaoCausa avaliacaoCausa, String causaEfetiva,
		AvaliacaoSolucao avaliacaoSolucao, String solucaoEfetiva,
		Instant primeiraOcorrenciaTs, Instant ultimaOcorrenciaTs, Integer contagemOcorrencias,
		Boolean orcamentoDebitado) {

	public static TicketDetailDto from(Ticket t) {
		return new TicketDetailDto(t.getId(), t.getApp(), t.getTipoErro(), t.getChaveDedup(), t.getData(),
				t.getTraceId(), t.getStatus(), t.getCausaRaiz(), t.getCausaCategoria(), t.getConfiancaCausa(),
				t.getSolucaoProposta(), t.getConfiancaSolucao(), t.getInvestigadoEm(), t.getFontesConsultadas(),
				t.getResolvidoEm(), t.getResolvidoPor(), t.getAvaliacaoCausa(), t.getCausaEfetiva(),
				t.getAvaliacaoSolucao(), t.getSolucaoEfetiva(), t.getPrimeiraOcorrenciaTs(),
				t.getUltimaOcorrenciaTs(), t.getContagemOcorrencias(), t.getOrcamentoDebitado());
	}
}
