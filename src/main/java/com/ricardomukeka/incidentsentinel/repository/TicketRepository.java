package com.ricardomukeka.incidentsentinel.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ricardomukeka.incidentsentinel.enums.TipoErro;
import com.ricardomukeka.incidentsentinel.model.Ticket;

/** JpaSpecificationExecutor — filtros combináveis de app/status/data pro TicketController (docs/ARCHITECTURE.md). */
public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

	/**
	 * Dedup/associação atômica (docs/ARCHITECTURE.md) — INSERT ... ON DUPLICATE
	 * KEY UPDATE nativo, nunca capturar DataIntegrityViolationException na
	 * mesma transação (marca a transação como rollback-only no flush).
	 * Status inicial gravado como o nome do enum TicketStatus.EM_INVESTIGACAO
	 * (@Enumerated(EnumType.STRING) — ver Ticket).
	 *
	 * <p>O affected rows retornado (1 = insert novo, 2 = update via ON
	 * DUPLICATE KEY no MySQL) decide se o chamador enfileira para
	 * investigação ou só associa a ocorrência. Só é garantia sob
	 * {@code useAffectedRows=true} na URL do datasource (docs/ARCHITECTURE.md) —
	 * sem essa flag o retorno de 1/2 não é confiável.
	 *
	 * <p>{@code traceId} entra no INSERT mas fica FORA da cláusula ON
	 * DUPLICATE KEY UPDATE — mesmo padrão já usado aqui pra
	 * {@code primeira_ocorrencia_ts}: guarda o trace da ocorrência que abriu
	 * o ticket, nunca sobrescreve em ocorrências repetidas (docs/ARCHITECTURE.md).
	 *
	 * <p><b>{@code ultima_ocorrencia_ts} usa {@code GREATEST}, não
	 * sobrescrita incondicional</b> (bug corrigido em 2026-08-21, achado de
	 * revisão): o SigNoz devolve os sinais do mais novo pro mais antigo
	 * (ver {@code SignozQueryTemplateFactory}, {@code OrderBy.desc}), e o
	 * coletor processa a lista nessa mesma ordem, sem reordenar. Com
	 * {@code = VALUES(...)} puro, processar 3 sinais desc (12:00:03,
	 * 12:00:02, 12:00:01) deixava gravado o MAIS ANTIGO dos três — dado
	 * exposto pro time de plantão via {@code TicketController}.
	 * {@code GREATEST} garante que o valor final é sempre o maior visto,
	 * independente da ordem de chegada.
	 */
	@Modifying
	@Query(value = "INSERT INTO ticket "
			+ "(app, tipo_erro, chave_dedup, data, status, primeira_ocorrencia_ts, ultima_ocorrencia_ts, contagem_ocorrencias, trace_id) "
			+ "VALUES (:app, :tipoErro, :chaveDedup, :data, 'EM_INVESTIGACAO', :ocorrenciaTs, :ocorrenciaTs, 1, :traceId) "
			+ "ON DUPLICATE KEY UPDATE contagem_ocorrencias = contagem_ocorrencias + 1, "
			+ "ultima_ocorrencia_ts = GREATEST(ultima_ocorrencia_ts, VALUES(ultima_ocorrencia_ts))",
			nativeQuery = true)
	int upsertOcorrencia(@Param("app") String app, @Param("tipoErro") String tipoErro,
			@Param("chaveDedup") String chaveDedup, @Param("data") LocalDate data,
			@Param("ocorrenciaTs") Instant ocorrenciaTs, @Param("traceId") String traceId);

	/**
	 * Usado por TriageServiceImpl só quando upsertOcorrencia retorna 1 (insert
	 * novo) pra descobrir o id gerado e publicar na fila (docs/ARCHITECTURE.md, passo 5)
	 * — a query nativa de INSERT não devolve o id gerado no mesmo round-trip.
	 */
	Optional<Long> findIdByAppAndTipoErroAndChaveDedupAndData(String app, TipoErro tipoErro, String chaveDedup,
			LocalDate data);

	/**
	 * Guarda de idempotência contra redelivery do Pub/Sub (docs/ARCHITECTURE.md) —
	 * a chave de dedup evita duas MENSAGENS diferentes investigarem o mesmo
	 * erro, mas não evita a MESMA mensagem, reprocessada após nack, debitar
	 * orçamento de novo. Só incrementa investigation_budget se isto afetar 1
	 * linha; 0 linhas = redelivery de mensagem cujo orçamento já foi debitado.
	 */
	@Modifying
	@Query("UPDATE Ticket t SET t.orcamentoDebitado = true WHERE t.id = :id AND t.orcamentoDebitado = false")
	int marcarOrcamentoDebitado(@Param("id") Long ticketId);
}
