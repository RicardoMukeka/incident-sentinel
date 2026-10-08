package com.ricardomukeka.incidentsentinel.ticket;

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ricardomukeka.incidentsentinel.dto.ErrorSignalDto;
import com.ricardomukeka.incidentsentinel.enums.SinalOutcome;
import com.ricardomukeka.incidentsentinel.model.ErrorSignalLog;
import com.ricardomukeka.incidentsentinel.repository.ErrorSignalLogRepository;
import com.ricardomukeka.incidentsentinel.repository.TicketRepository;
import com.ricardomukeka.incidentsentinel.triage.DedupKeyBuilder;

/**
 * Dedup/associação de ocorrência (docs/ARCHITECTURE.md) — extraído de
 * TriageServiceImpl (docs/ARCHITECTURE.md, passo 9) pra separar "decidir se um sinal
 * vira ticket novo ou associação" (aqui) de "o que fazer depois disso"
 * (enfileirar, no caso de TriageServiceImpl).
 *
 * <p>As duas escritas (ticket + error_signal_log) continuam uma única
 * transação — é isso que torna o watermark derivado (docs/ARCHITECTURE.md)
 * seguro: um registro em error_signal_log só existe se o ticket também foi
 * gravado com sucesso. Seguro fazer porque INSERT...ON DUPLICATE KEY UPDATE
 * nunca lança violação de constraint — não marca a transação como
 * rollback-only, diferente de capturar DataIntegrityViolationException
 * (banido, docs/ARCHITECTURE.md).
 */
@Service
public class OccurrenceAssociationService {

	private final TicketRepository ticketRepository;
	private final ErrorSignalLogRepository errorSignalLogRepository;

	public OccurrenceAssociationService(TicketRepository ticketRepository,
			ErrorSignalLogRepository errorSignalLogRepository) {
		this.ticketRepository = ticketRepository;
		this.errorSignalLogRepository = errorSignalLogRepository;
	}

	@Transactional
	public OccurrenceAssociationResult associar(ErrorSignalDto sinal) {
		String chaveDedup = DedupKeyBuilder.build(sinal.app(), sinal.tipoErro(), sinal.atributoExtra());
		// "dia" é sempre calculado na JVM em UTC, nunca CURRENT_DATE/NOW() do MySQL — mesma
		// fronteira de "dia" usada pelo orçamento diário (docs/ARCHITECTURE.md).
		LocalDate hoje = LocalDate.now(ZoneOffset.UTC);

		int affectedRows = ticketRepository.upsertOcorrencia(sinal.app(), sinal.tipoErro().name(), chaveDedup, hoje,
				sinal.timestamp(), sinal.traceId());
		// 1 = INSERT novo, 2 = UPDATE via ON DUPLICATE KEY (MySQL, com useAffectedRows=true) —
		// docs/ARCHITECTURE.md, garantia depende dessa flag na URL do datasource.
		SinalOutcome outcome = affectedRows == 1 ? SinalOutcome.NOVO_TICKET : SinalOutcome.ASSOCIADO;

		ErrorSignalLog signalLog = new ErrorSignalLog();
		signalLog.setApp(sinal.app());
		signalLog.setTipoErro(sinal.tipoErro());
		signalLog.setChaveDedup(chaveDedup);
		signalLog.setTraceId(sinal.traceId());
		signalLog.setTimestamp(sinal.timestamp());
		signalLog.setOutcome(outcome);
		errorSignalLogRepository.save(signalLog);

		if (outcome != SinalOutcome.NOVO_TICKET) {
			return new OccurrenceAssociationResult(outcome, null);
		}

		Long ticketId = ticketRepository
				.findIdByAppAndTipoErroAndChaveDedupAndData(sinal.app(), sinal.tipoErro(), chaveDedup, hoje)
				.orElseThrow(() -> new IllegalStateException(
						"Ticket recém-criado não encontrado (app=%s, tipoErro=%s, chaveDedup=%s, data=%s)"
								.formatted(sinal.app(), sinal.tipoErro(), chaveDedup, hoje)));
		return new OccurrenceAssociationResult(outcome, ticketId);
	}
}
