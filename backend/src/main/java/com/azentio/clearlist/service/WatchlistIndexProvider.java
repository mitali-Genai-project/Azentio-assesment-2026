package com.azentio.clearlist.service;

import com.azentio.clearlist.entity.MatchedOn;
import com.azentio.clearlist.entity.WatchlistEntry;
import com.azentio.clearlist.matcher.NameNormalizer;
import com.azentio.clearlist.matcher.NormalizedName;
import com.azentio.clearlist.matcher.WatchlistIndex;
import com.azentio.clearlist.matcher.WatchlistIndex.IndexedEntry;
import com.azentio.clearlist.matcher.WatchlistIndex.IndexedName;
import com.azentio.clearlist.repository.WatchlistEntryRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Holds the pre-normalised index for the most recently requested watchlist version, so names are not
 * re-read and re-normalised on every screening. Safe because versions are immutable: an index built for
 * version N is valid forever, and a request for any other version id simply rebuilds.
 */
@Component
public class WatchlistIndexProvider {

    private static final Logger log = LoggerFactory.getLogger(WatchlistIndexProvider.class);

    private final WatchlistEntryRepository entryRepository;
    private final NameNormalizer normalizer;
    private volatile WatchlistIndex cached;

    public WatchlistIndexProvider(WatchlistEntryRepository entryRepository, NameNormalizer normalizer) {
        this.entryRepository = entryRepository;
        this.normalizer = normalizer;
    }

    public WatchlistIndex indexFor(long versionId) {
        WatchlistIndex index = cached;
        if (index != null && index.versionId() == versionId) {
            return index;
        }
        synchronized (this) {
            index = cached;
            if (index == null || index.versionId() != versionId) {
                index = build(versionId);
                cached = index;
            }
            return index;
        }
    }

    private WatchlistIndex build(long versionId) {
        long start = System.nanoTime();
        List<IndexedEntry> entries = entryRepository.findAllWithAliasesByVersionId(versionId).stream()
                .map(this::toIndexedEntry)
                .toList();
        log.info("Built watchlist index for version {} with {} entries in {} ms",
                versionId, entries.size(), (System.nanoTime() - start) / 1_000_000);
        return new WatchlistIndex(versionId, entries);
    }

    private IndexedEntry toIndexedEntry(WatchlistEntry entry) {
        List<IndexedName> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        addName(names, seen, entry.getPrimaryName(), MatchedOn.PRIMARY); // primary first: wins ties
        for (String alias : entry.getAliases()) {
            addName(names, seen, alias, MatchedOn.ALIAS);
        }
        return new IndexedEntry(entry.getId(), entry.getEntryRef(), entry.getListType(), names);
    }

    private void addName(List<IndexedName> names, Set<String> seen, String original, MatchedOn matchedOn) {
        NormalizedName normalized = normalizer.normalize(original);
        // Skip aliases that normalise to the same tokens as a name already indexed (e.g. accent-free copies)
        if (!normalized.isEmpty() && seen.add(normalized.canonicalKey())) {
            names.add(new IndexedName(original, normalized, matchedOn));
        }
    }
}
