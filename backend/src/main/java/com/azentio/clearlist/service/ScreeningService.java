package com.azentio.clearlist.service;

import com.azentio.clearlist.cache.ScreeningCache;
import com.azentio.clearlist.cache.ScreeningCacheKey;
import com.azentio.clearlist.config.MatchingProperties;
import com.azentio.clearlist.dto.HitResponse;
import com.azentio.clearlist.dto.ScreeningRequest;
import com.azentio.clearlist.dto.ScreeningResponse;
import com.azentio.clearlist.entity.Decision;
import com.azentio.clearlist.entity.Screening;
import com.azentio.clearlist.entity.ScreeningHit;
import com.azentio.clearlist.entity.WatchlistVersion;
import com.azentio.clearlist.exception.InvalidNameException;
import com.azentio.clearlist.mapper.ScreeningMapper;
import com.azentio.clearlist.matcher.MatchCandidate;
import com.azentio.clearlist.matcher.NameNormalizer;
import com.azentio.clearlist.matcher.NormalizedName;
import com.azentio.clearlist.matcher.WatchlistMatcher;
import com.azentio.clearlist.repository.ScreeningHitRepository;
import com.azentio.clearlist.repository.ScreeningRepository;
import com.azentio.clearlist.repository.WatchlistEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScreeningService {

    private static final Logger log = LoggerFactory.getLogger(ScreeningService.class);

    private final NameNormalizer normalizer;
    private final WatchlistService watchlistService;
    private final WatchlistIndexProvider indexProvider;
    private final WatchlistMatcher matcher;
    private final ScreeningCache cache;
    private final ScreeningRepository screeningRepository;
    private final ScreeningHitRepository hitRepository;
    private final WatchlistEntryRepository entryRepository;
    private final Clock clock;
    private final int maxHitsInResponse;

    public ScreeningService(NameNormalizer normalizer, WatchlistService watchlistService,
                            WatchlistIndexProvider indexProvider, WatchlistMatcher matcher, ScreeningCache cache,
                            ScreeningRepository screeningRepository, ScreeningHitRepository hitRepository,
                            WatchlistEntryRepository entryRepository, Clock clock, MatchingProperties properties) {
        this.normalizer = normalizer;
        this.watchlistService = watchlistService;
        this.indexProvider = indexProvider;
        this.matcher = matcher;
        this.cache = cache;
        this.screeningRepository = screeningRepository;
        this.hitRepository = hitRepository;
        this.entryRepository = entryRepository;
        this.clock = clock;
        this.maxHitsInResponse = properties.maxHits();
    }

    /**
     * Screens one customer against the current watchlist version. The version is resolved once and used
     * for both matching and the persisted record, so a concurrent upload cannot mix versions.
     */
    @Transactional
    public ScreeningResponse screen(ScreeningRequest request) {
        NormalizedName name = normalizer.normalize(request.fullName());
        if (name.isEmpty()) {
            throw new InvalidNameException();
        }
        WatchlistVersion version = watchlistService.requireCurrentVersion();

        // The key is scoped to the version resolved above, so a result cached for an older version
        // can never be returned after a new list is uploaded.
        ScreeningCacheKey cacheKey = new ScreeningCacheKey(version.getId(), name.canonicalKey());
        Optional<List<MatchCandidate>> cached = cache.get(cacheKey);
        boolean cacheHit = cached.isPresent();
        List<MatchCandidate> matches = cached.orElseGet(() -> {
            List<MatchCandidate> computed = matcher.findMatches(indexProvider.indexFor(version.getId()), name);
            cache.put(cacheKey, computed);
            return computed;
        });

        // A screening record is created for every request, cached or not.
        Instant now = Instant.now(clock);
        Decision decision = matches.isEmpty() ? Decision.CLEAR : Decision.POTENTIAL_MATCH;
        Screening screening = screeningRepository.save(new Screening(request.customerRef(), request.fullName(),
                request.dob(), request.nationality(), version, decision, cacheHit, now));

        List<ScreeningHit> hits = hitRepository.saveAll(matches.stream()
                .map(m -> new ScreeningHit(screening, entryRepository.getReferenceById(m.entryDbId()),
                        m.matchedName(), m.matchedOn(), m.score(), now))
                .toList());

        // Customer name/DOB are deliberately not logged (PII); customerRef is an internal identifier.
        log.info("Screening {} for customerRef={} against version {}: {} ({} hits, cacheHit={})",
                screening.getId(), request.customerRef(), version.getId(), decision, hits.size(), cacheHit);

        // hits are saved in the same order as matches (best first); build DTOs from the candidates so the
        // lazy watchlist-entry references are never loaded
        List<HitResponse> topHits = new ArrayList<>();
        for (int i = 0; i < Math.min(maxHitsInResponse, hits.size()); i++) {
            topHits.add(ScreeningMapper.toHitResponse(hits.get(i), matches.get(i)));
        }
        return new ScreeningResponse(screening.getId(), request.customerRef(), version.getId(), decision,
                hits.size(), topHits, cacheHit, now);
    }
}
