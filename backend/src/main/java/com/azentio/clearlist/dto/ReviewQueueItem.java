package com.azentio.clearlist.dto;

import com.azentio.clearlist.entity.HitStatus;
import com.azentio.clearlist.entity.ListType;
import com.azentio.clearlist.entity.MatchedOn;
import java.time.Instant;
import java.util.UUID;

public record ReviewQueueItem(
        UUID hitId,
        UUID screeningId,
        String customerRef,
        String entryId,
        String matchedName,
        MatchedOn matchedOn,
        double score,
        ListType listType,
        long listVersion,
        HitStatus status,
        Instant createdAt) {
}
