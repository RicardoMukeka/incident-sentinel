package com.ricardomukeka.incidentsentinel.triage;

/**
 * Enfileira um tíquete recém-criado pra investigação (docs/ARCHITECTURE.md).
 * Implementação real: NovoErroPub (Pub/Sub, docs/ARCHITECTURE.md passo 5, molde de
 * service-checkout). O payload publicado é só o id do ticket — mensagem
 * mínima, o consumer relê o estado atual do banco em vez de carregar dados
 * potencialmente desatualizados no corpo da mensagem.
 */
public interface NovoTicketPublisher {

	void publicar(Long ticketId);
}
