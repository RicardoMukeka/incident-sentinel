package com.ricardomukeka.incidentsentinel.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ricardomukeka.incidentsentinel.enums.TipoErro;
import com.ricardomukeka.incidentsentinel.model.ErrorSignalLog;

public interface ErrorSignalLogRepository extends JpaRepository<ErrorSignalLog, Long> {

	/**
	 * Fonte do watermark (docs/ARCHITECTURE.md) — leitura pura sobre coluna
	 * indexada, sempre consistente com o que já foi commitado. Por
	 * app + tipo_erro, nunca agregado só por app (achado pós-4ª rodada:
	 * evita que falha isolada num tipo seja mascarada pelo avanço de outro).
	 */
	@Query("SELECT MAX(e.timestamp) FROM ErrorSignalLog e WHERE e.app = :app AND e.tipoErro = :tipoErro")
	Optional<Instant> findMaxTimestamp(@Param("app") String app, @Param("tipoErro") TipoErro tipoErro);
}
