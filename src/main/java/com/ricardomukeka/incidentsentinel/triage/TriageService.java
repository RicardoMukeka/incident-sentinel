package com.ricardomukeka.incidentsentinel.triage;

import com.ricardomukeka.incidentsentinel.dto.ErrorSignalDto;

/**
 * Triagem determinística, sem LLM (docs/ARCHITECTURE.md) — dedup via
 * INSERT...ON DUPLICATE KEY UPDATE nativo (TicketRepository.upsertOcorrencia)
 * + gravação de error_signal_log, as duas na mesma transação.
 *
 * <p><b>Implementação real é o passo 4 de docs/ARCHITECTURE.md, ainda não feito.</b>
 * Esta interface existe agora só pra CollectorService (passo 3) já nascer
 * com o ponto de hand-off correto, sem persistir nada em memória entre os
 * dois passos (docs/ARCHITECTURE.md).
 */
public interface TriageService {

	void processar(ErrorSignalDto sinal);
}
