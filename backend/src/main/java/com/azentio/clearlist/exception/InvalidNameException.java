package com.azentio.clearlist.exception;

import org.springframework.http.HttpStatus;

/** The name has no matchable content after normalisation (e.g. only "Mr." or punctuation). */
public class InvalidNameException extends ApiException {

    public InvalidNameException() {
        super(HttpStatus.BAD_REQUEST, "INVALID_NAME", "fullName contains no matchable name tokens");
    }
}
