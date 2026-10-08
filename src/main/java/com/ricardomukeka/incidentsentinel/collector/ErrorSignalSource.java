package com.ricardomukeka.incidentsentinel.collector;

import java.time.Instant;
import java.util.List;

import com.ricardomukeka.incidentsentinel.dto.ErrorSignalDto;
import com.ricardomukeka.incidentsentinel.enums.TipoErro;

/**
 * Único gancho de extensibilidade de fonte de sinal (docs/ARCHITECTURE.md). V1:
 * só SignozErrorSignalSource. Fase 2 (Prometheus) e Fase 3 (ServiceNow)
 * implementam a mesma interface sem tocar em storage/triage/pubsub/investigation.
 */
public interface ErrorSignalSource {

	List<ErrorSignalDto> fetchSignals(TipoErro tipoErro, String serviceName, Instant since);
}
