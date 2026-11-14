package com.azentio.clearlist.exception;

import com.azentio.clearlist.dto.ErrorResponse.Detail;
import java.util.List;
import org.springframework.http.HttpStatus;

/** The upload contained malformed rows; nothing was stored. */
public class WatchlistValidationException extends ApiException {

    public WatchlistValidationException(String message, List<Detail> details) {
        super(HttpStatus.BAD_REQUEST, "MALFORMED_WATCHLIST", message, details);
    }
}
