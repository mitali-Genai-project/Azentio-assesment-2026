package com.azentio.clearlist.matcher;

import com.azentio.clearlist.entity.ListType;
import com.azentio.clearlist.entity.MatchedOn;

/** One watchlist entry that scored at or above the threshold. Plain data so it can be cached. */
public record MatchCandidate(
        long entryDbId,
        String entryId,
        String matchedName,
        MatchedOn matchedOn,
        double score,
        ListType listType) {
}
