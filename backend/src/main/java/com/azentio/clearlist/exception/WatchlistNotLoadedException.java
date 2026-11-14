package com.azentio.clearlist.exception;

import org.springframework.http.HttpStatus;

/** Screening was requested before any watchlist version was uploaded. */
public class WatchlistNotLoadedException extends ApiException {

    public WatchlistNotLoadedException() {
        super(HttpStatus.CONFLICT, "WATCHLIST_NOT_LOADED",
                "No watchlist version has been loaded; upload a watchlist before screening");
    }
}
