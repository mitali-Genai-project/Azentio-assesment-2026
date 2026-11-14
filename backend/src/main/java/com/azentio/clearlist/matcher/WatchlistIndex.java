package com.azentio.clearlist.matcher;

import com.azentio.clearlist.entity.ListType;
import com.azentio.clearlist.entity.MatchedOn;
import java.util.List;

/**
 * Immutable, pre-normalised in-memory view of one watchlist version. Because versions never change,
 * an index built once for a version id stays valid for the life of the process.
 */
public record WatchlistIndex(long versionId, List<IndexedEntry> entries) {

    public WatchlistIndex {
        entries = List.copyOf(entries);
    }

    public record IndexedEntry(long entryDbId, String entryId, ListType listType, List<IndexedName> names) {
        public IndexedEntry {
            names = List.copyOf(names);
        }
    }

    public record IndexedName(String original, NormalizedName normalized, MatchedOn matchedOn) {
    }
}
