package com.ricardomukeka.incidentsentinel.exception;

public class McpToolException extends RuntimeException {

	public McpToolException(String message, Throwable cause) {
		super(message, cause);
	}

	public McpToolException(String message) {
		super(message);
	}
}
