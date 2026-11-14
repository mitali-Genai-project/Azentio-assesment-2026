package com.azentio.clearlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azentio.clearlist.cache.InMemoryScreeningCache;
import com.azentio.clearlist.cache.ScreeningCacheKey;
import com.azentio.clearlist.config.MatchingProperties;
import com.azentio.clearlist.dto.ScreeningRequest;
import com.azentio.clearlist.dto.ScreeningResponse;
import com.azentio.clearlist.entity.ListType;
import com.azentio.clearlist.entity.MatchedOn;
import com.azentio.clearlist.entity.Screening;
import com.azentio.clearlist.entity.WatchlistVersion;
import com.azentio.clearlist.matcher.MatchCandidate;
import com.azentio.clearlist.matcher.NameNormalizer;
import com.azentio.clearlist.matcher.WatchlistIndex;
import com.azentio.clearlist.matcher.WatchlistMatcher;
import com.azentio.clearlist.repository.ScreeningHitRepository;
import com.azentio.clearlist.repository.ScreeningRepository;
import com.azentio.clearlist.repository.WatchlistEntryRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** Cache behaviour of ScreeningService, with Mockito for persistence/matching and the real in-memory cache. */
class ScreeningServiceCacheTest {

    private final WatchlistService watchlistService = mock(WatchlistService.class);
    private final WatchlistIndexProvider indexProvider = mock(WatchlistIndexProvider.class);
    private final WatchlistMatcher matcher = mock(WatchlistMatcher.class);
    private final ScreeningRepository screeningRepository = mock(ScreeningRepository.class);
    private final ScreeningHitRepository hitRepository = mock(ScreeningHitRepository.class);
    private final WatchlistEntryRepository entryRepository = mock(WatchlistEntryRepository.class);
    private final InMemoryScreeningCache cache = new InMemoryScreeningCache(Duration.ofMinutes(10), Clock.systemUTC());
    private final NameNormalizer normalizer = new NameNormalizer();

    private ScreeningService service;

    @BeforeEach
    void setUp() {
        service = new ScreeningService(normalizer, watchlistService, indexProvider, matcher, cache,
                screeningRepository, hitRepository, entryRepository, Clock.systemUTC(),
                new MatchingProperties(0.85, 0.88, 10));
        when(screeningRepository.save(any(Screening.class))).thenAnswer(inv -> inv.getArgument(0));
        when(hitRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(indexProvider.indexFor(any(Long.class))).thenAnswer(inv -> new WatchlistIndex(inv.getArgument(0), List.of()));
    }

    @Test
    void secondIdenticalRequestIsServedFromCacheButStillPersisted() {
        currentVersion(1L);
        when(matcher.findMatches(any(), any())).thenReturn(matches(1L));

        ScreeningResponse first = service.screen(request("Aleksandr Petrov"));
        ScreeningResponse second = service.screen(request("PETROV, Mr Aleksandr")); // same canonical name

        assertThat(first.cacheHit()).isFalse();
        assertThat(second.cacheHit()).isTrue();
        assertThat(second.decision()).isEqualTo(first.decision());
        verify(matcher, times(1)).findMatches(any(), any());

        ArgumentCaptor<Screening> saved = ArgumentCaptor.forClass(Screening.class);
        verify(screeningRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Screening::isCacheHit).containsExactly(false, true);
        verify(hitRepository, times(2)).saveAll(anyList());
    }

    @Test
    void newWatchlistVersionNeverUsesResultCachedForOldVersion() {
        currentVersion(1L);
        when(matcher.findMatches(any(), any())).thenReturn(matches(1L));
        service.screen(request("Aleksandr Petrov"));

        currentVersion(2L);
        when(matcher.findMatches(any(), any())).thenReturn(List.of()); // Petrov removed in version 2
        ScreeningResponse afterUpload = service.screen(request("Aleksandr Petrov"));

        assertThat(afterUpload.cacheHit()).isFalse();
        assertThat(afterUpload.listVersion()).isEqualTo(2L);
        assertThat(afterUpload.topHits()).isEmpty();
        verify(indexProvider).indexFor(2L);
    }

    @Test
    void cachedResultIsReusedOnlyForItsOwnVersion() {
        cache.put(new ScreeningCacheKey(5L, normalizer.normalize("Aleksandr Petrov").canonicalKey()), matches(5L));
        currentVersion(5L);

        ScreeningResponse response = service.screen(request("Aleksandr Petrov"));

        assertThat(response.cacheHit()).isTrue();
        verify(matcher, never()).findMatches(any(), any());
    }

    private void currentVersion(long id) {
        WatchlistVersion version = new WatchlistVersion("list.csv", 1, java.time.Instant.now(), "test");
        ReflectionTestUtils.setField(version, "id", id);
        when(watchlistService.requireCurrentVersion()).thenReturn(version);
    }

    private static List<MatchCandidate> matches(long entryDbId) {
        return List.of(new MatchCandidate(entryDbId, "WL-1", "Aleksandr Petrov", MatchedOn.PRIMARY, 1.0,
                ListType.SANCTIONS));
    }

    private static ScreeningRequest request(String name) {
        return new ScreeningRequest("CUST-1", name, null, null);
    }
}
