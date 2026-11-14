package com.azentio.clearlist.mapper;

import com.azentio.clearlist.dto.HitResponse;
import com.azentio.clearlist.dto.ReviewQueueItem;
import com.azentio.clearlist.dto.WatchlistVersionResponse;
import com.azentio.clearlist.entity.Screening;
import com.azentio.clearlist.entity.ScreeningHit;
import com.azentio.clearlist.entity.WatchlistVersion;
import com.azentio.clearlist.matcher.MatchCandidate;

/** Entity -> DTO conversion. Entities never leave the service layer. */
public final class ScreeningMapper {

    private ScreeningMapper() {
    }

    public static WatchlistVersionResponse toVersionResponse(WatchlistVersion version) {
        return new WatchlistVersionResponse(version.getId(), version.getEntryCount(), version.getLoadedAt(),
                version.getSourceFilename());
    }

    /** For a freshly created hit: entry details come from the match candidate, not the lazy entry proxy. */
    public static HitResponse toHitResponse(ScreeningHit hit, MatchCandidate match) {
        return new HitResponse(hit.getId(), match.entryId(), match.matchedName(), match.score(),
                match.matchedOn(), match.listType(), hit.getStatus());
    }

    /** For an existing hit loaded inside a transaction (entry details read from the entity). */
    public static HitResponse toHitResponse(ScreeningHit hit) {
        return new HitResponse(hit.getId(), hit.getWatchlistEntry().getEntryRef(), hit.getMatchedName(),
                hit.getScore(), hit.getMatchedOn(), hit.getWatchlistEntry().getListType(), hit.getStatus());
    }

    public static ReviewQueueItem toReviewQueueItem(ScreeningHit hit) {
        Screening screening = hit.getScreening();
        return new ReviewQueueItem(hit.getId(), screening.getId(), screening.getCustomerRef(),
                hit.getWatchlistEntry().getEntryRef(), hit.getMatchedName(), hit.getMatchedOn(), hit.getScore(),
                hit.getWatchlistEntry().getListType(), screening.getWatchlistVersion().getId(), hit.getStatus(),
                hit.getCreatedAt());
    }
}
