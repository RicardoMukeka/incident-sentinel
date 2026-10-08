package com.ricardomukeka.incidentsentinel.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ricardomukeka.incidentsentinel.enums.ExecucaoStatus;
import com.ricardomukeka.incidentsentinel.model.AppRegistry;

public interface AppRegistryRepository extends JpaRepository<AppRegistry, String> {

	List<AppRegistry> findByAtivoTrue();

	/**
	 * UPDATE em lote pro heartbeat de início de ciclo (docs/ARCHITECTURE.md) —
	 * substitui um loop de findById+save por app (achado de revisão,
	 * 2026-08-21: 2 round-trips por app monitorado, a cada 5 min, sem
	 * necessidade — nenhuma regra de negócio depende de ler a entidade
	 * antes de gravar o heartbeat). Mesmo padrão de UPDATE único já usado em
	 * TicketRepository.marcarOrcamentoDebitado.
	 */
	@Modifying
	@Query("UPDATE AppRegistry a SET a.ultimaExecucaoInicio = :agora, a.ultimaExecucaoStatus = :status "
			+ "WHERE a.serviceName IN :serviceNames")
	void marcarInicioCicloEmLote(@Param("serviceNames") List<String> serviceNames, @Param("agora") Instant agora,
			@Param("status") ExecucaoStatus status);

	/** Mesma lógica de {@link #marcarInicioCicloEmLote}, pro heartbeat de fim de ciclo. */
	@Modifying
	@Query("UPDATE AppRegistry a SET a.ultimaExecucaoFim = :agora, a.ultimaExecucaoStatus = :status "
			+ "WHERE a.serviceName IN :serviceNames")
	void marcarFimCicloEmLote(@Param("serviceNames") List<String> serviceNames, @Param("agora") Instant agora,
			@Param("status") ExecucaoStatus status);
}
