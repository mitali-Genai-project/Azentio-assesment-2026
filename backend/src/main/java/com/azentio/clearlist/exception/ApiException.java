package com.azentio.clearlist.exception;

import com.azentio.clearlist.dto.ErrorResponse.Detail;
import java.util.List;
import org.springframework.http.HttpStatus;

/** Base for expected business errors; carries the HTTP status and a stable error code. */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<Detail> details;

    protected ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    protected ApiException(HttpStatus status, String code, String message, List<Detail> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = List.copyOf(details);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<Detail> getDetails() {
        return details;
    }
}
