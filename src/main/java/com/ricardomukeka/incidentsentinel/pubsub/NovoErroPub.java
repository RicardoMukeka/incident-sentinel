package com.ricardomukeka.incidentsentinel.pubsub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ricardomukeka.incidentsentinel.triage.NovoTicketPublisher;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Molde de service-checkout/pubsub/ValidacaoConcluidaPub.java (docs/ARCHITECTURE.md
 * passo 5). Payload é só o id do ticket como string — mensagem mínima, sem
 * envelope JSON (não há nada mais a carregar; o consumer relê o ticket do
 * banco, não confia em dado potencialmente desatualizado na mensagem).
 *
 * <p><b>Falha de publish é observada, não descartada</b> (achado de revisão,
 * 2026-08-21). O ticket já foi commitado como {@code EM_INVESTIGACAO} numa
 * transação separada e já concluída ({@code OccurrenceAssociationService})
 * antes desta chamada — uma falha aqui não pode mais reverter isso. O
 * melhor que dá pra fazer é tornar a falha visível (log + métrica) em vez de
 * deixar o {@code CompletableFuture} de {@code publish(...)} morrer em
 * silêncio, pra alguém detectar e reprocessar manualmente um ticket preso em
 * {@code EM_INVESTIGACAO}. Não há retry automático aqui — se isso vier a
 * importar, é extensão futura, não coberta por este fix.
 */
@Component
public class NovoErroPub implements NovoTicketPublisher {

	private static final Logger log = LoggerFactory.getLogger(NovoErroPub.class);

	private final PubSubTemplate pubSubTemplate;
	private final MeterRegistry meterRegistry;

	@Value("${spring.cloud.gcp.pubsub.incidentsentinel.novo-erro.topic}")
	private String topicName;

	public NovoErroPub(PubSubTemplate pubSubTemplate, MeterRegistry meterRegistry) {
		this.pubSubTemplate = pubSubTemplate;
		this.meterRegistry = meterRegistry;
	}

	@Override
	public void publicar(Long ticketId) {
		log.info("Publicando ticket {} no tópico {}", ticketId, topicName);
		pubSubTemplate.publish(topicName, String.valueOf(ticketId)).whenComplete((messageId, ex) -> {
			if (ex != null) {
				meterRegistry.counter("incidentsentinel.pubsub.publish.failure", "topic", topicName).increment();
				log.error("Falha ao publicar ticket {} no tópico {} — investigação não será enfileirada, ticket "
						+ "fica preso em EM_INVESTIGACAO até intervenção manual", ticketId, topicName, ex);
			}
		});
	}
}
