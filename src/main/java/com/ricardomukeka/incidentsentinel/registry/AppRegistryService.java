package com.ricardomukeka.incidentsentinel.registry;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ricardomukeka.incidentsentinel.enums.ExecucaoStatus;
import com.ricardomukeka.incidentsentinel.model.AppRegistry;
import com.ricardomukeka.incidentsentinel.repository.AppRegistryRepository;

/**
 * Heartbeat de execução do coletor (docs/ARCHITECTURE.md) — UPDATE em lote por
 * ciclo, sem exigência de atomicidade forte (last-write-wins é aceitável,
 * diferente do orçamento/dedup). Gravado no início E no fim do ciclo.
 *
 * <p><b>UPDATE em lote, não loop de findById+save</b> (achado de revisão,
 * 2026-08-21) — a versão anterior fazia 1 findById + 1 save por app, em
 * loop, tanto no início quanto no fim: 4 round-trips ao banco por app
 * monitorado, a cada 5 min. Nenhuma regra de negócio depende de ler a
 * entidade antes de gravar o heartbeat, então um UPDATE único cobrindo
 * todos os {@code serviceNames} de uma vez (mesmo padrão já usado em
 * {@code TicketRepository.marcarOrcamentoDebitado}) faz o mesmo trabalho em
 * 1 round-trip, independente de quantos apps estão ativos.
 */
@Service
public class AppRegistryService {

	private final AppRegistryRepository appRegistryRepository;

	public AppRegistryService(AppRegistryRepository appRegistryRepository) {
		this.appRegistryRepository = appRegistryRepository;
	}

	public List<AppRegistry> listarAtivos() {
		return appRegistryRepository.findByAtivoTrue();
	}

	@Transactional
	public void marcarInicioCiclo(List<String> serviceNames) {
		if (serviceNames.isEmpty()) {
			return;
		}
		appRegistryRepository.marcarInicioCicloEmLote(serviceNames, Instant.now(), ExecucaoStatus.EM_ANDAMENTO);
	}

	@Transactional
	public void marcarFimCiclo(List<String> serviceNames, ExecucaoStatus status) {
		if (serviceNames.isEmpty()) {
			return;
		}
		appRegistryRepository.marcarFimCicloEmLote(serviceNames, Instant.now(), status);
	}
}
