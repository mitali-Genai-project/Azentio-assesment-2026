package com.azentio.clearlist.matcher;

import com.azentio.clearlist.config.MatchingProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Component;

/**
 * Token-aware fuzzy name similarity in the range 0..1.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Identical compact forms score 1.0 (handles spacing differences such as "AlHassan" vs "Al Hassan").</li>
 *   <li>Two single-token names are compared directly. A single-token name never matches a multi-token
 *       name: a lone surname ("Petrov") must not match "Aleksandr Petrov".</li>
 *   <li>Otherwise align tokens order-insensitively: greedily pair the most similar tokens (Jaro-Winkler,
 *       {@value #INITIAL_SCORE} for an initial matching the first letter of a token, exact equality for
 *       tokens of up to {@value #SHORT_TOKEN_LENGTH} letters, and 0 when lengths differ by more than the ratio
 *       {@value #MIN_LENGTH_RATIO}, e.g. "ivan" vs the patronymic "ivanovich"). Pairs below the token
 *       threshold contribute 0, so a shared surname alone cannot reach the overall threshold.</li>
 *   <li>Score = sum of pair similarities / (tokens in the shorter name + {@value #EXTRA_TOKEN_WEIGHT}
 *       per extra token in the longer name). Missing middle names cost a little, not a lot.</li>
 *   <li>At least one matched pair must be a full word, not an initial, so "A. B." cannot match everyone.</li>
 * </ol>
 */
@Component
public class FuzzyNameMatcher {

    static final double INITIAL_SCORE = 0.9;
    static final double EXTRA_TOKEN_WEIGHT = 0.15;
    static final int SHORT_TOKEN_LENGTH = 3;
    static final double MIN_LENGTH_RATIO = 0.6;

    private final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();
    private final double tokenThreshold;

    public FuzzyNameMatcher(MatchingProperties properties) {
        this.tokenThreshold = properties.tokenThreshold();
    }

    public double score(NormalizedName first, NormalizedName second) {
        if (first.isEmpty() || second.isEmpty()) {
            return 0.0;
        }
        if (first.compact().equals(second.compact())) {
            return 1.0;
        }
        NormalizedName shorter = first.size() <= second.size() ? first : second;
        NormalizedName longer = shorter == first ? second : first;

        if (longer.size() == 1) {
            return tokenSimilarity(shorter.compact(), longer.compact());
        }
        if (shorter.size() == 1) {
            // One name token cannot identify a person listed under several (a shared surname is not a match).
            return 0.0;
        }
        return alignedScore(shorter.tokens(), longer.tokens());
    }

    private double alignedScore(List<String> shorter, List<String> longer) {
        List<TokenPair> candidates = new ArrayList<>();
        for (int i = 0; i < shorter.size(); i++) {
            for (int j = 0; j < longer.size(); j++) {
                double similarity = tokenSimilarity(shorter.get(i), longer.get(j));
                if (similarity >= tokenThreshold) {
                    candidates.add(new TokenPair(i, j, similarity, isInitial(shorter.get(i)) || isInitial(longer.get(j))));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(TokenPair::similarity).reversed());

        boolean[] shorterUsed = new boolean[shorter.size()];
        boolean[] longerUsed = new boolean[longer.size()];
        double total = 0.0;
        boolean anyFullWordMatch = false;
        for (TokenPair pair : candidates) {
            if (shorterUsed[pair.shorterIndex()] || longerUsed[pair.longerIndex()]) {
                continue;
            }
            shorterUsed[pair.shorterIndex()] = true;
            longerUsed[pair.longerIndex()] = true;
            total += pair.similarity();
            anyFullWordMatch |= !pair.involvesInitial();
        }
        if (!anyFullWordMatch) {
            return 0.0;
        }
        double denominator = shorter.size() + EXTRA_TOKEN_WEIGHT * (longer.size() - shorter.size());
        return Math.min(1.0, total / denominator);
    }

    private double tokenSimilarity(String a, String b) {
        if (isInitial(a) || isInitial(b)) {
            if (a.charAt(0) != b.charAt(0)) {
                return 0.0;
            }
            return a.length() == b.length() ? 1.0 : INITIAL_SCORE;
        }
        if (a.length() <= SHORT_TOKEN_LENGTH || b.length() <= SHORT_TOKEN_LENGTH) {
            // Jaro-Winkler is unreliable on very short strings ("hu" vs "hui" = 0.91): require equality.
            return a.equals(b) ? 1.0 : 0.0;
        }
        if ((double) Math.min(a.length(), b.length()) / Math.max(a.length(), b.length()) < MIN_LENGTH_RATIO) {
            // Jaro-Winkler's prefix bonus rates "ivan" ~ "ivanovich" (a patronymic) highly; spelling variants
            // of one name have similar lengths, so very different lengths mean different name parts.
            return 0.0;
        }
        return jaroWinkler.apply(a, b);
    }

    private static boolean isInitial(String token) {
        return token.length() == 1;
    }

    private record TokenPair(int shorterIndex, int longerIndex, double similarity, boolean involvesInitial) {
    }
}
