package com.ricardomukeka.incidentsentinel.pubsub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import com.ricardomukeka.incidentsentinel.enums.TicketStatus;
import com.ricardomukeka.incidentsentinel.investigation.InvestigationBudgetService;
import com.ricardomukeka.incidentsentinel.investigation.InvestigationOrchestrator;
import com.ricardomukeka.incidentsentinel.investigation.InvestigationOutcome;
import com.ricardomukeka.incidentsentinel.investigation.ResultadoOrcamento;
import com.ricardomukeka.incidentsentinel.model.Ticket;
import com.ricardomukeka.incidentsentinel.repository.TicketRepository;
import com.ricardomukeka.incidentsentinel.ticket.TicketService;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.google.cloud.spring.pubsub.support.GcpPubSubHeaders;

/**
 * Molde de service-checkout/pubsub/ValidacaoPendenteSub.java (docs/ARCHITECTURE.md
 * passo 5/6/8/9). Orçamento diário verificado ANTES de qualquer chamada ao
 * LLM (docs/ARCHITECTURE.md); {@code PROSSEGUIR} aciona o InvestigationOrchestrator
 * de verdade. A partir do passo 9, a mutação do Ticket fica em TicketService
 * (pacote ticket/), não inline aqui.
 */
@Service
public class NovoErroSub {

	private static final Logger log = LoggerFactory.getLogger(NovoErroSub.class);

	private final TicketRepository ticketRepository;
	private final InvestigationBudgetService investigationBudgetService;
	private final InvestigationOrchestrator investigationOrchestrator;
	private final TicketService ticketService;

	public NovoErroSub(TicketRepository ticketRepository, InvestigationBudgetService investigationBudgetService,
			InvestigationOrchestrator investigationOrchestrator, TicketService ticketService) {
		this.ticketRepository = ticketRepository;
		this.investigationBudgetService = investigationBudgetService;
		this.investigationOrchestrator = investigationOrchestrator;
		this.ticketService = ticketService;
	}

	@ServiceActivator(inputChannel = "novoErroChannel")
	public void listenNovoErro(Message<String> pubMessage) {
		BasicAcknowledgeablePubsubMessage ackMessage = pubMessage.getHeaders().get(GcpPubSubHeaders.ORIGINAL_MESSAGE,
				BasicAcknowledgeablePubsubMessage.class);

		try {
			Long ticketId = Long.valueOf(pubMessage.getPayload());
			log.info("Processando ticket {}", ticketId);

			processar(ticketId);

			acknowledgeMessage(ackMessage, true);
		} catch (Exception e) {
			log.error("Erro no processamento do payload {}: {}", pubMessage.getPayload(), e.getMessage(), e);
			acknowledgeMessage(ackMessage, false);
		}
	}

	/**
	 * Não é {@code @Transactional} de propósito (achado de revisão, 2026-08-21).
	 * Era anotado antes, mas a anotação nunca teve efeito — chamado via
	 * self-invocation ({@code this.processar(...)} de dentro de
	 * {@code listenNovoErro}, mesma classe), o que não passa pelo proxy AOP
	 * do Spring. Removida em vez de "consertada" (ex.: self-injection):
	 * envolver isto numa transação de verdade seria pior, não melhor —
	 * manteria uma conexão de banco aberta durante os "dezenas de segundos"
	 * de {@code investigationOrchestrator.investigar} (chamada ao LLM),
	 * risco real de esgotar o pool de conexões sob investigações
	 * concorrentes. Não há necessidade real de atomicidade cobrindo o
	 * método inteiro: {@code investigationBudgetService.avaliar} já é
	 * {@code @Transactional} por conta própria (docs/ARCHITECTURE.md), e
	 * {@code TicketService.*} só chama {@code TicketRepository.save}, que já
	 * é atômico por si só (Spring Data JPA).
	 */
	void processar(Long ticketId) {
		Ticket ticket = ticketRepository.findById(ticketId).orElse(null);
		if (ticket == null) {
			log.warn("Ticket {} não encontrado — nada a processar, mensagem tratada como sucesso", ticketId);
			return;
		}

		// Guarda contra re-pagamento (docs/ARCHITECTURE.md): se o ticket já saiu de EM_INVESTIGACAO,
		// esta é uma redelivery de uma mensagem cujo processamento já terminou (o ack anterior não
		// chegou, ou o save final falhou DEPOIS da chamada de LLM já paga). Não reinvestiga.
		if (ticket.getStatus() != TicketStatus.EM_INVESTIGACAO) {
			log.info("Ticket {} já está em {} — redelivery, nada a reprocessar", ticketId, ticket.getStatus());
			return;
		}

		ResultadoOrcamento resultado = investigationBudgetService.avaliar(ticketId);
		switch (resultado) {
			case DESABILITADO -> ticketService.marcarDesabilitada(ticket);
			case ORCAMENTO_EXCEDIDO -> ticketService.marcarOrcamentoExcedido(ticket);
			case PROSSEGUIR -> {
				InvestigationOutcome outcome = investigationOrchestrator.investigar(ticketId, ticket.getApp(),
						ticket.getTipoErro(), ticket.getChaveDedup(), ticket.getTraceId(),
						ticket.getPrimeiraOcorrenciaTs());
				ticketService.aplicarDesfechoInvestigacao(ticket, outcome);
			}
		}
	}

	private void acknowledgeMessage(BasicAcknowledgeablePubsubMessage ackMessage, boolean success) {
		if (ackMessage != null) {
			if (success) {
				ackMessage.ack();
			} else {
				ackMessage.nack();
			}
		}
	}
}
