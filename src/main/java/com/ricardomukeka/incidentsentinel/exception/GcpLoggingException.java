package com.ricardomukeka.incidentsentinel.exception;

/** Falha ao consultar o GCP Cloud Logging no retrieval da investigação (docs/ARCHITECTURE.md). */
public class GcpLoggingException extends RuntimeException {

	public GcpLoggingException(String message, Throwable cause) {
		super(message, cause);
	}
}
