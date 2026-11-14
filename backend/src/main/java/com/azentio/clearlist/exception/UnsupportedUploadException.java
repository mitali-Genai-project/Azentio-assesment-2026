package com.azentio.clearlist.exception;

import org.springframework.http.HttpStatus;

public class UnsupportedUploadException extends ApiException {

    public UnsupportedUploadException(String message) {
        super(HttpStatus.BAD_REQUEST, "UNSUPPORTED_FILE_TYPE", message);
    }
}
