package com.azentio.clearlist.cache;

import com.azentio.clearlist.matcher.MatchCandidate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local cache for offline tests and local runs. Expired entries are dropped when read.
 * Unbounded, so not intended for production (use {@link RedisScreeningCache}).
 */
public class InMemoryScreeningCache implements ScreeningCache {

    private final Map<ScreeningCacheKey, Entry> entries = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    public InMemoryScreeningCache(Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public Optional<List<MatchCandidate>> get(ScreeningCacheKey key) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(entry.expiresAt())) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.matches());
    }

    @Override
    public void put(ScreeningCacheKey key, List<MatchCandidate> matches) {
        entries.put(key, new Entry(List.copyOf(matches), clock.instant().plus(ttl)));
    }

    private record Entry(List<MatchCandidate> matches, Instant expiresAt) {
    }
}
