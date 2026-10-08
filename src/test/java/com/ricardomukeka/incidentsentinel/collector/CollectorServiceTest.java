package com.ricardomukeka.incidentsentinel.collector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ricardomukeka.incidentsentinel.config.properties.CollectorProperties;
import com.ricardomukeka.incidentsentinel.enums.ExecucaoStatus;
import com.ricardomukeka.incidentsentinel.model.AppRegistry;
import com.ricardomukeka.incidentsentinel.registry.AppRegistryService;
import com.ricardomukeka.incidentsentinel.repository.ErrorSignalLogRepository;
import com.ricardomukeka.incidentsentinel.triage.TriageService;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Regressão do bug encontrado na revisão de arquitetura (2026-08-21):
 * listarAtivos/marcarInicioCiclo rodavam ANTES do try, então uma exceção ali
 * pulava o finally inteiro (sem métrica de duração, sem heartbeat de fim,
 * status preso em "em andamento" pra sempre). Agora rodam dentro do try.
 */
@ExtendWith(MockitoExtension.class)
class CollectorServiceTest {

	@Mock
	private AppRegistryService appRegistryService;

	@Mock
	private ErrorSignalLogRepository errorSignalLogRepository;

	@Mock
	private ErrorSignalSource errorSignalSource;

	@Mock
	private TriageService triageService;

	private final CollectorProperties collectorProperties = new CollectorProperties("segredo", Duration.ofSeconds(30),
			Duration.ofHours(1), Duration.ofHours(24), Duration.ofSeconds(2));

	private CollectorService collectorService;

	private CollectorService novoCollectorService() {
		return new CollectorService(appRegistryService, errorSignalLogRepository, errorSignalSource,
				collectorProperties, triageService, new SimpleMeterRegistry());
	}

	@Test
	void run_caminhoFeliz_marcaInicioEFimComSucesso() {
		AppRegistry app = new AppRegistry();
		app.setServiceName("service-orders");
		when(appRegistryService.listarAtivos()).thenReturn(List.of(app));
		when(errorSignalSource.fetchSignals(any(), any(), any())).thenReturn(List.of());
		collectorService = novoCollectorService();

		collectorService.run();

		verify(appRegistryService).marcarInicioCiclo(List.of("service-orders"));
		verify(appRegistryService).marcarFimCiclo(List.of("service-orders"), ExecucaoStatus.SUCESSO);
	}

	@Test
	void run_listarAtivosFalha_naoChamaMarcarFimCiclo_masNaoLancaExcecao() {
		when(appRegistryService.listarAtivos()).thenThrow(new RuntimeException("banco indisponível"));
		collectorService = novoCollectorService();

		collectorService.run();

		verify(appRegistryService, never()).marcarInicioCiclo(anyList());
		verify(appRegistryService, never()).marcarFimCiclo(anyList(), any());
	}

	@Test
	void run_marcarInicioCicloFalha_aindaAssimMarcaFimCicloComFalha() {
		AppRegistry app = new AppRegistry();
		app.setServiceName("service-orders");
		when(appRegistryService.listarAtivos()).thenReturn(List.of(app));
		doThrowRuntimeExceptionOnMarcarInicioCiclo();
		collectorService = novoCollectorService();

		collectorService.run();

		verify(appRegistryService).marcarFimCiclo(List.of("service-orders"), ExecucaoStatus.FALHA);
	}

	/**
	 * Regressão do bug encontrado na revisão de arquitetura (2026-08-21):
	 * buscarSinaisIsolado já engole toda RuntimeException internamente, então
	 * uma fonte externa 100% fora do ar (ex.: SigNoz com API key revogada)
	 * marcava SUCESSO mesmo assim — nenhuma exceção nunca chegava no catch
	 * externo de run().
	 */
	@Test
	void run_todasAsBuscasDeSinalFalham_marcaFimCicloComFalha() {
		AppRegistry app = new AppRegistry();
		app.setServiceName("service-orders");
		when(appRegistryService.listarAtivos()).thenReturn(List.of(app));
		when(errorSignalSource.fetchSignals(any(), any(), any()))
				.thenThrow(new RuntimeException("SigNoz indisponível"));
		collectorService = novoCollectorService();

		collectorService.run();

		verify(appRegistryService).marcarFimCiclo(List.of("service-orders"), ExecucaoStatus.FALHA);
	}

	@Test
	void run_semAppsAtivos_naoChamaMarcarFimCiclo() {
		when(appRegistryService.listarAtivos()).thenReturn(List.of());
		collectorService = novoCollectorService();

		collectorService.run();

		verify(appRegistryService, never()).marcarFimCiclo(anyList(), any());
	}

	private void doThrowRuntimeExceptionOnMarcarInicioCiclo() {
		org.mockito.Mockito.doThrow(new RuntimeException("falha ao gravar heartbeat")).when(appRegistryService)
				.marcarInicioCiclo(List.of("service-orders"));
	}
}
