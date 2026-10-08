package com.ricardomukeka.incidentsentinel.pubsub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import com.ricardomukeka.incidentsentinel.model.Ticket;
import com.ricardomukeka.incidentsentinel.repository.TicketRepository;
import com.ricardomukeka.incidentsentinel.ticket.TicketService;

/**
 * Molde de service-checkout/pubsub/ValidacaoPendenteDLTSub.java. Canal DLT é
 * AckMode.AUTO — só persiste a falha, sem ack/nack manual (docs/ARCHITECTURE.md).
 * Marca {@code investigacao_falhou_infra} desde o passo 5, não como
 * acréscimo depois (docs/ARCHITECTURE.md). A partir do passo 9, a mutação do
 * Ticket fica em TicketService (pacote ticket/), não inline aqui.
 */
@Service
public class NovoErroDLTSub {

	private static final Logger log = LoggerFactory.getLogger(NovoErroDLTSub.class);

	private final TicketRepository ticketRepository;
	private final TicketService ticketService;

	public NovoErroDLTSub(TicketRepository ticketRepository, TicketService ticketService) {
		this.ticketRepository = ticketRepository;
		this.ticketService = ticketService;
	}

	@ServiceActivator(inputChannel = "novoErroDLTChannel")
	public void listenNovoErroError(Message<String> pubMessage) {
		log.error("NOVO ERRO - Limite de tentativas excedido payload: {}", pubMessage.getPayload());

		try {
			Long ticketId = Long.valueOf(pubMessage.getPayload());
			marcarFalhaInfra(ticketId);
		} catch (NumberFormatException e) {
			log.error("Payload da DLT não é um id de ticket válido: {}", pubMessage.getPayload(), e);
		}
	}

	/**
	 * Não é {@code @Transactional} de propósito — mesmo achado de revisão de
	 * {@code NovoErroSub.processar} (2026-08-21): a anotação nunca teve
	 * efeito aqui (self-invocation de {@code listenNovoErroError}, mesma
	 * classe, não passa pelo proxy do Spring). Removida em vez de corrigida
	 * porque não há necessidade real de atomicidade entre o {@code findById}
	 * e {@code TicketService.marcarFalhaInfra} (que já salva atomicamente
	 * via {@code TicketRepository.save}) — não há chamada lenta nem outro
	 * escritor concorrente esperado pra este ticket específico neste ponto.
	 */
	void marcarFalhaInfra(Long ticketId) {
		Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
		if (ticket == null) {
			log.warn("Ticket {} não encontrado ao processar DLT — nada a marcar", ticketId);
			return;
		}

		ticketService.marcarFalhaInfra(ticket);
	}
}
