package com.azentio.clearlist.exception;

import org.springframework.http.HttpStatus;

public class EmptyWatchlistException extends ApiException {

    public EmptyWatchlistException() {
        super(HttpStatus.BAD_REQUEST, "EMPTY_WATCHLIST", "The uploaded watchlist contains no entries");
    }
}
