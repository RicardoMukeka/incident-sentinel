package com.ricardomukeka.incidentsentinel.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ricardomukeka.incidentsentinel.exception.TicketNotFoundException;

/**
 * RFC 9457 (application/problem+json) — área nova, sem legado herdado dos
 * repos-irmãos a seguir aqui (mesmo critério já usado pros DTOs como
 * record), alinhado ao padrão mais moderno já em uso no siscom-common-api
 * (docs/ARCHITECTURE.md).
 */
@RestControllerAdvice
public class TicketExceptionHandler {

	@ExceptionHandler(TicketNotFoundException.class)
	public ProblemDetail handleNotFound(TicketNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}
}
