package com.azentio.clearlist.matcher;

import static org.assertj.core.api.Assertions.assertThat;

import com.azentio.clearlist.config.MatchingProperties;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Named matching cases, evaluated with the production default thresholds. */
class FuzzyNameMatcherTest {

    /** Mirrors the defaults in application.yml. */
    private static final MatchingProperties DEFAULTS = new MatchingProperties(0.85, 0.88, 10);

    private final NameNormalizer normalizer = new NameNormalizer();
    private final FuzzyNameMatcher matcher = new FuzzyNameMatcher(DEFAULTS);

    static Stream<Arguments> shouldMatch() {
        return Stream.of(
                // the three examples named in the assessment brief
                Arguments.of("brief: Aleksandr Petrov vs Alexander Petroff", "Aleksandr Petrov", "Alexander Petroff"),
                Arguments.of("brief: Dr. Chen Wei vs Wei Chen", "Dr. Chen Wei", "Wei Chen"),
                Arguments.of("brief: J. R. Fernandes vs Joao Ricardo Fernandes", "J. R. Fernandes",
                        "João Ricardo Fernandes"),
                Arguments.of("identical name", "Aleksandr Petrov", "Aleksandr Petrov"),
                Arguments.of("case and punctuation", "PETROV, aleksandr", "Aleksandr Petrov"),
                Arguments.of("swapped token order", "Petrov Aleksandr", "Aleksandr Petrov"),
                Arguments.of("transliteration Alexander/Aleksandr", "Alexander Petrov", "Aleksandr Petrov"),
                Arguments.of("honorific Mr", "Mr. Aleksandr Petrov", "Aleksandr Petrov"),
                Arguments.of("honorific Shri", "Shri Rajesh Sharma", "Rajesh Sharma"),
                Arguments.of("diacritics stripped", "Jose Munoz", "José Muñoz"),
                Arguments.of("missing middle name", "Aleksandr Petrov", "Aleksandr Ivanovich Petrov"),
                Arguments.of("initial for first name", "A. Petrov", "Aleksandr Petrov"),
                Arguments.of("Arabic article spelling", "Omar Alhakim", "Omar Al-Hakim"),
                Arguments.of("Mohammed/Muhammad", "Muhammad Al Masri", "Mohammed Al-Masri"),
                Arguments.of("Youssef/Yusuf", "Yusuf Haddad", "Youssef Haddad"),
                Arguments.of("pinyin/Wade-Giles surname", "Chang Wei", "Zhang Wei"),
                Arguments.of("combined: honorific + swap + transliteration", "Mr Petrov Alexander",
                        "Aleksandr Petrov"));
    }

    static Stream<Arguments> shouldNotMatch() {
        return Stream.of(
                Arguments.of("shared surname only", "Ivan Petrov", "Aleksandr Petrov"),
                Arguments.of("first name equals start of a patronymic", "Ivan Petrov", "Aleksandr Ivanovich Petrov"),
                Arguments.of("shared first name only", "Aleksandr Ivanov", "Aleksandr Petrov"),
                Arguments.of("lone surname", "Petrov", "Aleksandr Petrov"),
                Arguments.of("lone first name", "Aleksandr", "Aleksandr Petrov"),
                Arguments.of("similar but different surname", "Wei Huang", "Wei Zhang"),
                Arguments.of("similar but different given name", "Jamal Haddad", "Kamal Haddad"),
                Arguments.of("short tokens must be exact", "Hu Wei", "Hui Wei"),
                Arguments.of("initials only", "A. P.", "Aleksandr Petrov"),
                Arguments.of("completely different", "John Smith", "Aleksandr Petrov"));
    }

    @ParameterizedTest(name = "match: {0}")
    @MethodSource("shouldMatch")
    void scoresAtOrAboveThreshold(String description, String customer, String listed) {
        assertThat(score(customer, listed)).isGreaterThanOrEqualTo(DEFAULTS.threshold());
    }

    @ParameterizedTest(name = "no match: {0}")
    @MethodSource("shouldNotMatch")
    void scoresBelowThreshold(String description, String customer, String listed) {
        assertThat(score(customer, listed)).isLessThan(DEFAULTS.threshold());
    }

    @Test
    void identicalNamesScoreOne() {
        assertThat(score("Aleksandr Petrov", "aleksandr petrov")).isEqualTo(1.0);
    }

    @Test
    void scoreIsSymmetricAndWithinRange() {
        double forward = score("A. Petrov", "Aleksandr Ivanovich Petrov");
        double backward = score("Aleksandr Ivanovich Petrov", "A. Petrov");
        assertThat(forward).isEqualTo(backward).isBetween(0.0, 1.0);
    }

    @Test
    void missingMiddleNameCostsLessThanAWrongName() {
        double missingMiddle = score("Aleksandr Petrov", "Aleksandr Ivanovich Petrov");
        double wrongFirstName = score("Ivan Petrov", "Aleksandr Petrov");
        assertThat(missingMiddle).isLessThan(1.0).isGreaterThan(wrongFirstName);
    }

    @Test
    void emptyNameScoresZero() {
        assertThat(matcher.score(NormalizedName.EMPTY, normalizer.normalize("Aleksandr Petrov"))).isZero();
    }

    private double score(String a, String b) {
        return matcher.score(normalizer.normalize(a), normalizer.normalize(b));
    }
}
