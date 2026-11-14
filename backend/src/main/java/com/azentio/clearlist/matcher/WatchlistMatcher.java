package com.azentio.clearlist.matcher;

import com.azentio.clearlist.config.MatchingProperties;
import com.azentio.clearlist.matcher.WatchlistIndex.IndexedEntry;
import com.azentio.clearlist.matcher.WatchlistIndex.IndexedName;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Scores a customer name against every primary name and alias in a watchlist version.
 * Each entry yields at most one candidate: its best-scoring name. Only candidates at or above the
 * configured threshold are returned, best first.
 */
@Component
public class WatchlistMatcher {

    private final FuzzyNameMatcher nameMatcher;
    private final double threshold;

    public WatchlistMatcher(FuzzyNameMatcher nameMatcher, MatchingProperties properties) {
        this.nameMatcher = nameMatcher;
        this.threshold = properties.threshold();
    }

    public List<MatchCandidate> findMatches(WatchlistIndex index, NormalizedName customerName) {
        List<MatchCandidate> matches = new ArrayList<>();
        for (IndexedEntry entry : index.entries()) {
            IndexedName bestName = null;
            double bestScore = 0.0;
            for (IndexedName name : entry.names()) {
                double score = nameMatcher.score(customerName, name.normalized());
                // ">" keeps the primary name (listed first) when an alias ties with it
                if (score > bestScore) {
                    bestScore = score;
                    bestName = name;
                }
            }
            if (bestName != null && bestScore >= threshold) {
                matches.add(new MatchCandidate(entry.entryDbId(), entry.entryId(), bestName.original(),
                        bestName.matchedOn(), round(bestScore), entry.listType()));
            }
        }
        matches.sort(Comparator.comparingDouble(MatchCandidate::score).reversed()
                .thenComparing(MatchCandidate::entryId));
        return matches;
    }

    private static double round(double score) {
        return Math.round(score * 10_000) / 10_000.0;
    }
}
