package com.ricardomukeka.incidentsentinel.controller.internal;

import java.time.Instant;

import com.ricardomukeka.incidentsentinel.enums.ExecucaoStatus;

public record CollectorStatusDto(String app, Instant ultimaExecucaoInicio, Instant ultimaExecucaoFim,
		ExecucaoStatus ultimaExecucaoStatus) {
}
