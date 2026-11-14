package com.azentio.clearlist.dto;

import com.azentio.clearlist.entity.Decision;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param totalHits number of hits persisted for review; {@code topHits} is capped at the configured max-hits
 * @param cacheHit  whether the match result was served from the screening cache
 */
public record ScreeningResponse(
        UUID screeningId,
        String customerRef,
        long listVersion,
        Decision decision,
        int totalHits,
        List<HitResponse> topHits,
        boolean cacheHit,
        Instant screenedAt) {
}
