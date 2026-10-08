package com.ricardomukeka.incidentsentinel.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ricardomukeka.incidentsentinel.model.InvestigationBudget;

/**
 * Incremento sempre via UPDATE atômico — nunca SELECT + save() de entidade
 * (docs/ARCHITECTURE.md: read-modify-write não é atômico sob 2+ réplicas).
 * `dia` é sempre LocalDate.now(ZoneOffset.UTC) bindado pela JVM, nunca
 * CURRENT_DATE avaliado no servidor (docs/ARCHITECTURE.md — timezone único).
 */
public interface InvestigationBudgetRepository extends JpaRepository<InvestigationBudget, LocalDate> {

	/** Garante que a linha do dia exista antes do incremento — no-op se já existir. */
	@Modifying
	@Query(value = "INSERT INTO investigation_budget (dia, contador, tokens_acumulados, custo_acumulado_usd) "
			+ "VALUES (:dia, 0, 0, 0) ON DUPLICATE KEY UPDATE dia = dia", nativeQuery = true)
	void garantirLinhaDoDia(@Param("dia") LocalDate dia);

	/** Contador = investigações no dia (docs/ARCHITECTURE.md) — 1 incremento por investigação, não por chamada de LLM. */
	@Modifying
	@Query("UPDATE InvestigationBudget b SET b.contador = b.contador + 1 WHERE b.dia = :dia")
	int incrementarContador(@Param("dia") LocalDate dia);

	/** Rollup diário de tokens + custo (docs/ARCHITECTURE.md) — 1 incremento por chamada de LLM. */
	@Modifying
	@Query("UPDATE InvestigationBudget b SET b.tokensAcumulados = b.tokensAcumulados + :tokens, "
			+ "b.custoAcumuladoUsd = b.custoAcumuladoUsd + :custoUsd WHERE b.dia = :dia")
	int incrementarConsumo(@Param("dia") LocalDate dia, @Param("tokens") long tokens,
			@Param("custoUsd") BigDecimal custoUsd);
}
