package com.azentio.clearlist.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.azentio.clearlist.entity.ListType;
import com.azentio.clearlist.entity.MatchedOn;
import com.azentio.clearlist.matcher.MatchCandidate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryScreeningCacheTest {

    private static final List<MatchCandidate> MATCHES = List.of(
            new MatchCandidate(1L, "WL-1", "Aleksandr Petrov", MatchedOn.PRIMARY, 1.0, ListType.SANCTIONS));

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final InMemoryScreeningCache cache = new InMemoryScreeningCache(Duration.ofMinutes(10), clock);

    @Test
    void returnsStoredResultForSameVersionAndName() {
        cache.put(new ScreeningCacheKey(1, "aleksandr petruv"), MATCHES);

        assertThat(cache.get(new ScreeningCacheKey(1, "aleksandr petruv"))).contains(MATCHES);
    }

    @Test
    void cacheKeyChangesWhenListVersionChanges() {
        ScreeningCacheKey v1 = new ScreeningCacheKey(1, "aleksandr petruv");
        ScreeningCacheKey v2 = new ScreeningCacheKey(2, "aleksandr petruv");

        assertThat(v2).isNotEqualTo(v1);
        assertThat(v2.externalKey()).isNotEqualTo(v1.externalKey());
    }

    @Test
    void neverReturnsResultFromAnotherVersion() {
        cache.put(new ScreeningCacheKey(1, "aleksandr petruv"), MATCHES);

        assertThat(cache.get(new ScreeningCacheKey(2, "aleksandr petruv"))).isEmpty();
    }

    @Test
    void entriesExpireAfterTtl() {
        ScreeningCacheKey key = new ScreeningCacheKey(1, "aleksandr petruv");
        cache.put(key, MATCHES);

        clock.advance(Duration.ofMinutes(9));
        assertThat(cache.get(key)).isPresent();
        clock.advance(Duration.ofMinutes(1));
        assertThat(cache.get(key)).isEmpty();
    }

    @Test
    void externalKeyContainsVersionButNotThePlainName() {
        String key = new ScreeningCacheKey(7, "aleksandr petruv").externalKey();

        assertThat(key).startsWith("clearlist:screening:v7:").doesNotContain("aleksandr").doesNotContain("petruv");
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
