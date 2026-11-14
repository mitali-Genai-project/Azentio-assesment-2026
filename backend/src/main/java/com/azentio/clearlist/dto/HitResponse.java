package com.azentio.clearlist.dto;

import com.azentio.clearlist.entity.HitStatus;
import com.azentio.clearlist.entity.ListType;
import com.azentio.clearlist.entity.MatchedOn;
import java.util.UUID;

public record HitResponse(
        UUID hitId,
        String entryId,
        String matchedName,
        double score,
        MatchedOn matchedOn,
        ListType listType,
        HitStatus status) {
}
