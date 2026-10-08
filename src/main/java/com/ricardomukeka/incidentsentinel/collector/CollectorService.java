package com.ricardomukeka.incidentsentinel.collector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.ricardomukeka.incidentsentinel.config.properties.CollectorProperties;
import com.ricardomukeka.incidentsentinel.dto.ErrorSignalDto;
import com.ricardomukeka.incidentsentinel.enums.ExecucaoStatus;
import com.ricardomukeka.incidentsentinel.enums.TipoErro;
import com.ricardomukeka.incidentsentinel.model.AppRegistry;
import com.ricardomukeka.incidentsentinel.registry.AppRegistryService;
import com.ricardomukeka.incidentsentinel.repository.ErrorSignalLogRepository;
import com.ricardomukeka.incidentsentinel.triage.TriageService;
import com.ricardomukeka.incidentsentinel.util.DateWindowUtil;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Orquestra um ciclo do coletor (docs/ARCHITECTURE.md/§6.5). Disparado pelo
 * CollectorTriggerController via HTTP (Tsuru Job a cada 5 min) — não
 * {@code @Scheduled}. As N queries (apps ativos × 5 tipos) rodam em paralelo
 * via virtual threads; falha isolada num app+tipo não aborta o ciclo dos
 * demais; heartbeat gravado no início E no fim, em try/finally (docs/ARCHITECTURE.md).
 */
@Service
public class CollectorService {

	private static final Logger log = LoggerFactory.getLogger(CollectorService.class);

	private final AppRegistryService appRegistryService;
	private final ErrorSignalLogRepository errorSignalLogRepository;
	private final ErrorSignalSource errorSignalSource;
	private final CollectorProperties collectorProperties;
	private final TriageService triageService;
	private final MeterRegistry meterRegistry;

	public CollectorService(AppRegistryService appRegistryService, ErrorSignalLogRepository errorSignalLogRepository,
			ErrorSignalSource errorSignalSource, CollectorProperties collectorProperties,
			TriageService triageService, MeterRegistry meterRegistry) {
		this.appRegistryService = appRegistryService;
		this.errorSignalLogRepository = errorSignalLogRepository;
		this.errorSignalSource = errorSignalSource;
		this.collectorProperties = collectorProperties;
		this.triageService = triageService;
		this.meterRegistry = meterRegistry;
	}

	/** Disparado pelo CollectorTriggerController — dispara e retorna, não espera o ciclo terminar. */
	@Async
	public void runAsync() {
		run();
	}

	/**
	 * {@code listarAtivos}/{@code marcarInicioCiclo} rodam DENTRO do try (achado
	 * de revisão, 2026-08-21) — antes rodavam antes dele, então uma falha ali
	 * (ex.: blip transitório no banco) pulava o {@code finally} inteiro: nem a
	 * métrica de duração, nem o heartbeat de fim, nem status FALHA eram
	 * gravados, deixando {@code GET /internal/collector/status} preso em
	 * "em andamento" indefinidamente. {@code serviceNames} começa vazio e só é
	 * preenchido se {@code listarAtivos} tiver sucesso — se ele falhar, não há
	 * o que marcar em {@code marcarFimCiclo} (nem sabemos quais apps rodariam),
	 * mas a métrica de duração ainda é registrada.
	 */
	public void run() {
		Timer.Sample amostra = Timer.start(meterRegistry);
		List<String> serviceNames = List.of();
		boolean sucesso = true;
		try {
			List<AppRegistry> apps = appRegistryService.listarAtivos();
			serviceNames = apps.stream().map(AppRegistry::getServiceName).toList();
			appRegistryService.marcarInicioCiclo(serviceNames);

			List<ResultadoBusca> resultados = buscarResultadosEmParalelo(apps);
			// Achado de revisão (2026-08-21): buscarSinaisIsolado já engole toda
			// RuntimeException internamente, então o catch externo (abaixo) nunca via uma
			// fonte externa 100% fora do ar (ex.: SigNoz com API key revogada) — cada
			// combinação app+tipo falhava isolada, sinais ficava vazio, e o ciclo marcava
			// SUCESSO mesmo assim. !apps.isEmpty() evita falso positivo quando não há
			// nenhum app ativo (allMatch de stream vazio dá true por vacuidade).
			if (!apps.isEmpty() && resultados.stream().allMatch(ResultadoBusca::falhou)) {
				sucesso = false;
				log.error("Todas as {} buscas de sinal falharam neste ciclo — possível outage total de fonte externa",
						resultados.size());
			}
			List<ErrorSignalDto> sinais = resultados.stream().flatMap(r -> r.sinais().stream()).toList();
			for (ErrorSignalDto sinal : sinais) {
				processarComIsolamento(sinal);
			}
		} catch (RuntimeException ex) {
			sucesso = false;
			log.error("Ciclo do coletor falhou de forma não isolada (bug fora do try/catch por sinal)", ex);
		} finally {
			amostra.stop(meterRegistry.timer("incidentsentinel.collector.cycle.duration"));
			if (!serviceNames.isEmpty()) {
				appRegistryService.marcarFimCiclo(serviceNames,
						sucesso ? ExecucaoStatus.SUCESSO : ExecucaoStatus.FALHA);
			}
		}
	}

	private List<ResultadoBusca> buscarResultadosEmParalelo(List<AppRegistry> apps) {
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<CompletableFuture<ResultadoBusca>> futures = new ArrayList<>();
			for (AppRegistry app : apps) {
				for (TipoErro tipoErro : TipoErro.values()) {
					futures.add(CompletableFuture.supplyAsync(
							() -> buscarSinaisIsolado(app.getServiceName(), tipoErro), executor));
				}
			}
			return futures.stream().map(CompletableFuture::join).toList();
		}
	}

	/** Uma fonte/tipo falhando não quebra o ciclo dos demais (docs/ARCHITECTURE.md). */
	private ResultadoBusca buscarSinaisIsolado(String serviceName, TipoErro tipoErro) {
		try {
			Optional<Instant> watermark = errorSignalLogRepository.findMaxTimestamp(serviceName, tipoErro);
			Instant desde = DateWindowUtil.calcularDesde(watermark, Instant.now(),
					collectorProperties.watermarkOverlap(), collectorProperties.watermarkFallbackWindow(),
					collectorProperties.watermarkMaxLookback());
			return new ResultadoBusca(errorSignalSource.fetchSignals(tipoErro, serviceName, desde), false);
		} catch (RuntimeException ex) {
			// Achado de code review (2026-08-22): só logado, sem métrica — uma combinação
			// app+tipo que falha em TODO ciclo (ex.: SigNoz com auth quebrada só pra um serviço)
			// ficava invisível a alerta, diferente da falha de triagem (incidentsentinel.triage.signal.failure,
			// abaixo), que já emite métrica desde o desenho original.
			meterRegistry.counter("incidentsentinel.collector.signal.fetch.failure", "app", serviceName, "tipo_erro",
					tipoErro.name()).increment();
			log.warn("Falha ao buscar sinais de {}/{} — pulado neste ciclo, retomado no próximo", serviceName,
					tipoErro, ex);
			return new ResultadoBusca(List.of(), true);
		}
	}

	/** {@code falhou} distingue "sem sinais" (normal) de "não deu nem pra tentar" (achado de revisão, 2026-08-21). */
	private record ResultadoBusca(List<ErrorSignalDto> sinais, boolean falhou) {
	}

	/** Isolamento por sinal (docs/ARCHITECTURE.md) — falha num sinal não impede os demais do lote. */
	private void processarComIsolamento(ErrorSignalDto sinal) {
		try {
			triageService.processar(sinal);
		} catch (RuntimeException ex) {
			meterRegistry.counter("incidentsentinel.triage.signal.failure", "app", sinal.app(), "tipo_erro",
					sinal.tipoErro().name()).increment();
			log.warn("Falha ao triar sinal {} — demais sinais do lote continuam sendo processados", sinal, ex);
		}
	}
}
