package com.azentio.clearlist.matcher;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Converts free-text names into {@link NormalizedName} tokens.
 *
 * <ol>
 *   <li>lower-case (locale-independent)</li>
 *   <li>fold letters that Unicode does not decompose (ß, ł, ø, ...) and strip diacritics (é -> e)</li>
 *   <li>drop apostrophes so "O'Brien" stays one token; every other non-letter becomes a separator</li>
 *   <li>remove honorifics (Mr, Mrs, Dr, Shri, ...)</li>
 *   <li>join the Arabic article al/el onto the next token so "Al-Hakim", "Al Hakim" and "Alhakim" agree</li>
 *   <li>fold common romanisation differences (see {@link #foldTransliteration})</li>
 * </ol>
 * Token order is kept; order-insensitivity is handled by the matcher.
 */
@Component
public class NameNormalizer {

    // "sri" is deliberately absent: it is also a common given name (e.g. Indonesian names).
    private static final Set<String> HONORIFICS = Set.of(
            "mr", "mrs", "ms", "miss", "mx", "dr", "prof", "sir", "dame", "lord", "lady", "rev",
            "shri", "smt", "kumari", "hon");

    private static final Set<String> JOINED_ARTICLES = Set.of("al", "el");

    private static final Map<Character, String> NON_DECOMPOSABLE = Map.of(
            'ß', "ss", 'ø', "o", 'ł', "l", 'đ', "d", 'ð', "d",
            'þ', "th", 'æ', "ae", 'œ', "oe", 'ı', "i");

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern APOSTROPHES = Pattern.compile("['’`ʼ]");
    private static final Pattern NON_LETTERS = Pattern.compile("[^\\p{L}]+");
    private static final Pattern REPEATED_LETTER = Pattern.compile("(\\p{L})\\1+");

    /** Ordered rewrite rules: different romanisations of the same sound collapse to one spelling. */
    private static final String[][] TRANSLITERATION_RULES = {
            {"x", "ks"},   // Alexander / Aleksandr
            {"ph", "f"},
            {"ck", "k"},
            {"q", "k"},    // Qasim / Kassem, Tariq / Tarek
            {"zh", "ch"},  // pinyin Zhang / Wade-Giles Chang
            {"ou", "u"},   // Youssef / Yusuf, Iouri / Yuri
            {"oo", "u"},
            {"ee", "i"},   // Kareem / Karim
            {"y", "i"},    // Sergey / Sergei, Dmitry / Dmitri
            {"o", "u"},    // Omar / Umar, Mohammed / Muhammad
    };

    public NormalizedName normalize(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return NormalizedName.EMPTY;
        }
        String folded = foldCharacters(rawName.toLowerCase(Locale.ROOT));
        String stripped = COMBINING_MARKS.matcher(Normalizer.normalize(folded, Normalizer.Form.NFD)).replaceAll("");
        String noApostrophes = APOSTROPHES.matcher(stripped).replaceAll("");

        List<String> tokens = new ArrayList<>();
        String pendingArticle = null;
        for (String token : NON_LETTERS.split(noApostrophes)) {
            if (token.isEmpty() || HONORIFICS.contains(token)) {
                continue;
            }
            if (pendingArticle != null) {
                tokens.add(pendingArticle + token);
                pendingArticle = null;
            } else if (JOINED_ARTICLES.contains(token)) {
                pendingArticle = token;
            } else {
                tokens.add(token);
            }
        }
        if (pendingArticle != null) { // trailing "al" with nothing to attach to
            tokens.add(pendingArticle);
        }
        return new NormalizedName(tokens.stream().map(NameNormalizer::foldTransliteration).toList());
    }

    /**
     * Folds spelling variants that arise from transliteration (e.g. "yusuf"/"youssef", "zhang"/"chang") and
     * collapses doubled letters ("mohammed" -> "muhamed"). Applied to both sides of every comparison, so it
     * only ever makes equivalent spellings closer; Jaro-Winkler still sees the remaining differences.
     */
    static String foldTransliteration(String token) {
        String folded = token;
        for (String[] rule : TRANSLITERATION_RULES) {
            folded = folded.replace(rule[0], rule[1]);
        }
        return REPEATED_LETTER.matcher(folded).replaceAll("$1");
    }

    private static String foldCharacters(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            String replacement = NON_DECOMPOSABLE.get(c);
            if (replacement != null) {
                sb.append(replacement);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
