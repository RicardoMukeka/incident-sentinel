package com.ricardomukeka.incidentsentinel.ticket;

import com.ricardomukeka.incidentsentinel.enums.SinalOutcome;

/**
 * {@code ticketId} só é populado quando {@code outcome} é NOVO_TICKET — é o
 * único caso em que o chamador (TriageServiceImpl) precisa do id pra
 * publicar na fila (docs/ARCHITECTURE.md).
 */
public record OccurrenceAssociationResult(SinalOutcome outcome, Long ticketId) {
}
