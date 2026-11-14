package com.azentio.clearlist.cache;

import com.azentio.clearlist.matcher.MatchCandidate;
import java.util.List;
import java.util.Optional;

/**
 * Caches the result of matching a normalised name against one watchlist version. Only the pure matching
 * result is cached; every screening request still creates its own screening record and hits.
 *
 * <p>Implementations must never return a result computed for a different watchlist version than
 * {@link ScreeningCacheKey#versionId()}.
 */
public interface ScreeningCache {

    Optional<List<MatchCandidate>> get(ScreeningCacheKey key);

    void put(ScreeningCacheKey key, List<MatchCandidate> matches);
}
