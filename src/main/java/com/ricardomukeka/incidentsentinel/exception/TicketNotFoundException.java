package com.ricardomukeka.incidentsentinel.exception;

public class TicketNotFoundException extends RuntimeException {

	public TicketNotFoundException(Long id) {
		super("Ticket não encontrado: " + id);
	}
}
