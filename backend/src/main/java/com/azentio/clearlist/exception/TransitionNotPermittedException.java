package com.azentio.clearlist.exception;

import org.springframework.http.HttpStatus;

/** The caller's role may not perform this disposition (HTTP 403). */
public class TransitionNotPermittedException extends ApiException {

    public TransitionNotPermittedException(String message) {
        super(HttpStatus.FORBIDDEN, "TRANSITION_NOT_PERMITTED", message);
    }
}
