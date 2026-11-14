package com.azentio.clearlist.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Single error shape for every failure.
 *
 * @param error   stable machine-readable code (e.g. VALIDATION_FAILED, MALFORMED_WATCHLIST)
 * @param details per-field or per-row problems, omitted when there are none
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<Detail> details) {

    /** @param location field name, or "row N: field" for upload problems */
    public record Detail(String location, String message) {
    }
}
