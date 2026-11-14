package com.azentio.clearlist.matcher;

import static org.assertj.core.api.Assertions.assertThat;

import com.azentio.clearlist.config.MatchingProperties;
import com.azentio.clearlist.entity.ListType;
import com.azentio.clearlist.entity.MatchedOn;
import com.azentio.clearlist.matcher.WatchlistIndex.IndexedEntry;
import com.azentio.clearlist.matcher.WatchlistIndex.IndexedName;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WatchlistMatcherTest {

    private final NameNormalizer normalizer = new NameNormalizer();

    private final WatchlistIndex index = new WatchlistIndex(1L, List.of(
            entry(1, "WL-1", ListType.SANCTIONS, "Aleksandr Ivanovich Petrov", "Sasha Petrov"),
            entry(2, "WL-2", ListType.PEP, "Viktor Orlov", "Abu Karim"),
            entry(3, "WL-3", ListType.SANCTIONS, "Ivan Petrov")));

    @Test
    void matchesOnPrimaryName() {
        List<MatchCandidate> matches = matcher(0.85).findMatches(index, normalizer.normalize("Petrov Aleksandr"));

        assertThat(matches).singleElement().satisfies(m -> {
            assertThat(m.entryId()).isEqualTo("WL-1");
            assertThat(m.matchedOn()).isEqualTo(MatchedOn.PRIMARY);
            assertThat(m.matchedName()).isEqualTo("Aleksandr Ivanovich Petrov");
            assertThat(m.listType()).isEqualTo(ListType.SANCTIONS);
        });
    }

    @Test
    void matchesOnAliasAndReportsTheAlias() {
        List<MatchCandidate> matches = matcher(0.85).findMatches(index, normalizer.normalize("Abu Karim"));

        assertThat(matches).singleElement().satisfies(m -> {
            assertThat(m.entryId()).isEqualTo("WL-2");
            assertThat(m.matchedOn()).isEqualTo(MatchedOn.ALIAS);
            assertThat(m.matchedName()).isEqualTo("Abu Karim");
            assertThat(m.score()).isEqualTo(1.0);
        });
    }

    @Test
    void sharedSurnameAloneDoesNotMatchOtherEntries() {
        // "Ivan Petrov" must hit WL-3 only, never WL-1 (Aleksandr Petrov) via the shared surname
        List<MatchCandidate> matches = matcher(0.85).findMatches(index, normalizer.normalize("Ivan Petrov"));

        assertThat(matches).extracting(MatchCandidate::entryId).containsExactly("WL-3");
    }

    @Test
    void thresholdComesFromConfiguration() {
        NormalizedName missingMiddle = normalizer.normalize("Aleksandr Petrov");

        assertThat(matcher(0.85).findMatches(index, missingMiddle)).extracting(MatchCandidate::entryId)
                .containsExactly("WL-1");
        assertThat(matcher(0.99).findMatches(index, missingMiddle)).isEmpty();
    }

    @Test
    void returnsBestScoreFirst() {
        WatchlistIndex twoCandidates = new WatchlistIndex(2L, List.of(
                entry(10, "WL-A", ListType.PEP, "Aleksandr Ivanovich Petrov"),
                entry(11, "WL-B", ListType.PEP, "Aleksandr Petrov")));

        List<MatchCandidate> matches = matcher(0.85).findMatches(twoCandidates, normalizer.normalize("Aleksandr Petrov"));

        assertThat(matches).extracting(MatchCandidate::entryId).containsExactly("WL-B", "WL-A");
        assertThat(matches.get(0).score()).isGreaterThan(matches.get(1).score());
    }

    private WatchlistMatcher matcher(double threshold) {
        MatchingProperties properties = new MatchingProperties(threshold, 0.88, 10);
        return new WatchlistMatcher(new FuzzyNameMatcher(properties), properties);
    }

    private IndexedEntry entry(long id, String entryId, ListType type, String primary, String... aliases) {
        List<IndexedName> names = new ArrayList<>();
        names.add(new IndexedName(primary, normalizer.normalize(primary), MatchedOn.PRIMARY));
        for (String alias : aliases) {
            names.add(new IndexedName(alias, normalizer.normalize(alias), MatchedOn.ALIAS));
        }
        return new IndexedEntry(id, entryId, type, names);
    }
}
