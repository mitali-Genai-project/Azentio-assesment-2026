package com.azentio.clearlist.matcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class NameNormalizerTest {

    private final NameNormalizer normalizer = new NameNormalizer();

    static Stream<Arguments> equivalentNames() {
        return Stream.of(
                Arguments.of("case differences", "ALEKSANDR PETROV", "aleksandr petrov"),
                Arguments.of("diacritics", "José Muñoz", "Jose Munoz"),
                Arguments.of("letters Unicode does not decompose", "Łukasz Weiß", "Lukasz Weiss"),
                Arguments.of("punctuation and list-style comma", "PETROV, Aleksandr.", "Petrov Aleksandr"),
                Arguments.of("honorific Mr", "Mr. Aleksandr Petrov", "Aleksandr Petrov"),
                Arguments.of("honorific Mrs", "Mrs Anna Petrova", "Anna Petrova"),
                Arguments.of("honorific Dr", "Dr Rajesh Sharma", "Rajesh Sharma"),
                Arguments.of("honorific Shri", "Shri Rajesh Sharma", "Rajesh Sharma"),
                Arguments.of("apostrophe kept inside token", "Sean O'Brien", "Sean OBrien"),
                Arguments.of("Arabic article joined", "Omar Al-Hakim", "Omar Alhakim"),
                Arguments.of("Arabic article with space", "Omar al Hakim", "Omar Al-Hakim"),
                Arguments.of("transliteration y/i", "Sergey Dmitry", "Sergei Dmitri"),
                Arguments.of("transliteration x/ks", "Alexandr", "Aleksandr"),
                Arguments.of("transliteration zh/ch (pinyin vs Wade-Giles)", "Zhang Wei", "Chang Wei"),
                Arguments.of("transliteration o/u and double letters", "Mohammed", "Muhamed"),
                Arguments.of("extra whitespace", "  Aleksandr   Petrov ", "Aleksandr Petrov"));
    }

    @ParameterizedTest(name = "{0}: \"{1}\" == \"{2}\"")
    @MethodSource("equivalentNames")
    void normalisesEquivalentSpellingsToSameTokens(String description, String a, String b) {
        assertThat(normalizer.normalize(a)).isEqualTo(normalizer.normalize(b));
    }

    @Test
    void keepsTokenOrderButCanonicalKeyIgnoresIt() {
        NormalizedName forward = normalizer.normalize("Aleksandr Petrov");
        NormalizedName swapped = normalizer.normalize("Petrov Aleksandr");

        assertThat(forward.tokens()).containsExactly("aleksandr", "petruv");
        assertThat(swapped.tokens()).containsExactly("petruv", "aleksandr");
        assertThat(forward.canonicalKey()).isEqualTo(swapped.canonicalKey());
    }

    @Test
    void producesLowerCaseAsciiTokens() {
        assertThat(normalizer.normalize("Dr. Jürgen Müller-Lüdenscheidt").tokens())
                .containsExactly("jurgen", "muler", "ludenscheidt");
    }

    @Test
    void doesNotTreatSriAsHonorificBecauseItIsAlsoAGivenName() {
        assertThat(normalizer.normalize("Sri Mulyani").tokens()).hasSize(2);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "Mr.", "Dr Mrs", "-- ,."})
    void returnsEmptyWhenNothingMatchableRemains(String input) {
        assertThat(normalizer.normalize(input).isEmpty()).isTrue();
    }
}
