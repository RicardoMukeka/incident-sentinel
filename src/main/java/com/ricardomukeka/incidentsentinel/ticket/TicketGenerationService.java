package com.ricardomukeka.incidentsentinel.ticket;

import org.springframework.stereotype.Service;

import com.ricardomukeka.incidentsentinel.dto.CausaSolucaoResult;
import com.ricardomukeka.incidentsentinel.enums.CausaCategoria;
import com.ricardomukeka.incidentsentinel.enums.ConfiancaDiagnostico;
import com.ricardomukeka.incidentsentinel.enums.TicketStatus;
import com.ricardomukeka.incidentsentinel.investigation.RedactionService;

/**
 * Transforma o structured output do LLM (ou a falha dele) em campos de ticket
 * (docs/ARCHITECTURE.md). Aplica:
 * <ul>
 * <li>fallback de enum não reconhecido: {@code confianca*} nula → BAIXA,
 * {@code causaCategoria} nula → INDETERMINADA;</li>
 * <li>gate de baixa confiança: {@code confiancaCausa == BAIXA} →
 * {@code status = CAUSA_INCONCLUSIVA}, {@code solucaoProposta} descartada
 * (o LLM já deveria ter deixado vazia, mas garantimos aqui);</li>
 * <li>redação de segredo em {@code causaRaiz} e {@code solucaoProposta} antes
 * de qualquer coisa ser persistida.</li>
 * </ul>
 */
@Service
public class TicketGenerationService {

	private final RedactionService redactionService;

	public TicketGenerationService(RedactionService redactionService) {
		this.redactionService = redactionService;
	}

	public GeneratedTicketFields fromResultado(CausaSolucaoResult resultado) {
		if (resultado == null || resultado.causaRaiz() == null || resultado.causaRaiz().isBlank()) {
			return falhaParsing();
		}

		String causaRaiz = redactionService.redigir(resultado.causaRaiz());
		CausaCategoria categoria = resultado.causaCategoria() == null ? CausaCategoria.INDETERMINADA
				: resultado.causaCategoria();
		ConfiancaDiagnostico confiancaCausa = resultado.confiancaCausa() == null ? ConfiancaDiagnostico.BAIXA
				: resultado.confiancaCausa();

		if (confiancaCausa == ConfiancaDiagnostico.BAIXA) {
			// Gate (docs/ARCHITECTURE.md) — causa apurada mas incerta: sem solução, vai pra humano.
			return new GeneratedTicketFields(TicketStatus.CAUSA_INCONCLUSIVA, causaRaiz, categoria,
					ConfiancaDiagnostico.BAIXA, null, null);
		}

		String solucao = redactionService.redigir(resultado.solucaoProposta());
		ConfiancaDiagnostico confiancaSolucao = resultado.confiancaSolucao() == null ? ConfiancaDiagnostico.BAIXA
				: resultado.confiancaSolucao();

		return new GeneratedTicketFields(TicketStatus.ABERTO, causaRaiz, categoria, confiancaCausa, solucao,
				confiancaSolucao);
	}

	public GeneratedTicketFields falhaParsing() {
		return new GeneratedTicketFields(TicketStatus.INVESTIGACAO_FALHOU_PARSING, null, null, null, null, null);
	}
}
