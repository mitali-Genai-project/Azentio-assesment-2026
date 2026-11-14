package com.azentio.clearlist.cache;

import com.azentio.clearlist.matcher.MatchCandidate;
import java.util.List;
import java.util.Optional;

/** Caching disabled (clearlist.cache.type=none), e.g. for the cache-disabled load-test baseline. */
public class NoOpScreeningCache implements ScreeningCache {

    @Override
    public Optional<List<MatchCandidate>> get(ScreeningCacheKey key) {
        return Optional.empty();
    }

    @Override
    public void put(ScreeningCacheKey key, List<MatchCandidate> matches) {
        // intentionally nothing
    }
}
