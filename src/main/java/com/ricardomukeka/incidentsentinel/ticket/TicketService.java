package com.ricardomukeka.incidentsentinel.ticket;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.ricardomukeka.incidentsentinel.enums.TicketStatus;
import com.ricardomukeka.incidentsentinel.investigation.InvestigationOutcome;
import com.ricardomukeka.incidentsentinel.model.Ticket;
import com.ricardomukeka.incidentsentinel.repository.TicketRepository;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Centraliza a mutação/persistência do Ticket (docs/ARCHITECTURE.md, passo 9) — cada
 * método já sabe o desfecho que representa, então emite
 * {@code incidentsentinel.investigation.outcome} (Counter, tag outcome, docs/ARCHITECTURE.md
 * §6.7) direto.
 */
@Service
public class TicketService {

	private final TicketRepository ticketRepository;
	private final MeterRegistry meterRegistry;

	public TicketService(TicketRepository ticketRepository, MeterRegistry meterRegistry) {
		this.ticketRepository = ticketRepository;
		this.meterRegistry = meterRegistry;
	}

	public void marcarDesabilitada(Ticket ticket) {
		ticket.setStatus(TicketStatus.INVESTIGACAO_DESABILITADA);
		ticketRepository.save(ticket);
		incrementarOutcome("desabilitada");
	}

	public void marcarOrcamentoExcedido(Ticket ticket) {
		ticket.setStatus(TicketStatus.INVESTIGACAO_PULADA_ORCAMENTO_EXCEDIDO);
		ticketRepository.save(ticket);
		incrementarOutcome("orcamento_excedido");
	}

	public void marcarFalhaInfra(Ticket ticket) {
		ticket.setStatus(TicketStatus.INVESTIGACAO_FALHOU_INFRA);
		ticketRepository.save(ticket);
		incrementarOutcome("falha_infra");
	}

	public void aplicarDesfechoInvestigacao(Ticket ticket, InvestigationOutcome outcome) {
		ticket.setStatus(outcome.status());
		ticket.setCausaRaiz(outcome.causaRaiz());
		ticket.setCausaCategoria(outcome.causaCategoria());
		ticket.setConfiancaCausa(outcome.confiancaCausa());
		ticket.setSolucaoProposta(outcome.solucaoProposta());
		ticket.setConfiancaSolucao(outcome.confiancaSolucao());
		ticket.setFontesConsultadas(outcome.fontesConsultadas());
		ticket.setInvestigadoEm(Instant.now());
		ticketRepository.save(ticket);

		incrementarOutcome(switch (outcome.status()) {
			case INVESTIGACAO_FALHOU_PARSING -> "falha_parsing";
			case CAUSA_INCONCLUSIVA -> "inconclusivo";
			default -> "sucesso";
		});
	}

	private void incrementarOutcome(String outcome) {
		meterRegistry.counter("incidentsentinel.investigation.outcome", "outcome", outcome).increment();
	}
}
