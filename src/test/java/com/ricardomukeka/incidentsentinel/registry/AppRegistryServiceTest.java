package com.ricardomukeka.incidentsentinel.registry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ricardomukeka.incidentsentinel.enums.ExecucaoStatus;
import com.ricardomukeka.incidentsentinel.repository.AppRegistryRepository;

/**
 * Regressão do bug encontrado na revisão de arquitetura (2026-08-21): antes,
 * marcarInicioCiclo/marcarFimCiclo faziam 1 findById+save por app em loop (4
 * round-trips por app monitorado, por ciclo). Agora é 1 UPDATE em lote.
 */
@ExtendWith(MockitoExtension.class)
class AppRegistryServiceTest {

	@Mock
	private AppRegistryRepository appRegistryRepository;

	private AppRegistryService novoService() {
		return new AppRegistryService(appRegistryRepository);
	}

	@Test
	void marcarInicioCiclo_comApps_chamaUpdateEmLoteUmaVezSo() {
		AppRegistryService appRegistryService = novoService();

		appRegistryService.marcarInicioCiclo(List.of("service-orders", "service-checkout"));

		verify(appRegistryRepository).marcarInicioCicloEmLote(eq(List.of("service-orders", "service-checkout")),
				any(Instant.class), eq(ExecucaoStatus.EM_ANDAMENTO));
		verify(appRegistryRepository, never()).findById(org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	void marcarFimCiclo_comApps_chamaUpdateEmLoteUmaVezSo() {
		AppRegistryService appRegistryService = novoService();

		appRegistryService.marcarFimCiclo(List.of("service-orders"), ExecucaoStatus.SUCESSO);

		verify(appRegistryRepository).marcarFimCicloEmLote(eq(List.of("service-orders")), any(Instant.class),
				eq(ExecucaoStatus.SUCESSO));
	}

	@Test
	void marcarInicioCiclo_semApps_naoChamaORepositorio() {
		AppRegistryService appRegistryService = novoService();

		appRegistryService.marcarInicioCiclo(List.of());

		verifyNoInteractions(appRegistryRepository);
	}
}
