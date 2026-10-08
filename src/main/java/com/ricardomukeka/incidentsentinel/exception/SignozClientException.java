package com.ricardomukeka.incidentsentinel.exception;

public class SignozClientException extends RuntimeException {

	public SignozClientException(String message, Throwable cause) {
		super(message, cause);
	}

	public SignozClientException(String message) {
		super(message);
	}
}
