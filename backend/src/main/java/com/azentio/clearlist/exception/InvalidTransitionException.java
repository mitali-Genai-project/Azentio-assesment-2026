package com.azentio.clearlist.exception;

import org.springframework.http.HttpStatus;

/** The hit's current status does not allow the requested transition (HTTP 409). */
public class InvalidTransitionException extends ApiException {

    public InvalidTransitionException(String message) {
        super(HttpStatus.CONFLICT, "INVALID_TRANSITION", message);
    }
}
