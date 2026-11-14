package com.azentio.clearlist.matcher;

import java.util.List;

/**
 * A name reduced to comparable tokens (lower-case ASCII-folded letters, no honorifics, no punctuation).
 * Token order is preserved; the matcher itself is order-insensitive.
 */
public record NormalizedName(List<String> tokens) {

    public static final NormalizedName EMPTY = new NormalizedName(List.of());

    public NormalizedName {
        tokens = List.copyOf(tokens);
    }

    public boolean isEmpty() {
        return tokens.isEmpty();
    }

    public int size() {
        return tokens.size();
    }

    /** All tokens joined without separators, e.g. "al hassan" and "alhassan" both become "alhassan". */
    public String compact() {
        return String.join("", tokens);
    }

    /**
     * Order-insensitive canonical form. Two names with the same key always score identically against
     * any watchlist entry, so this is safe to use in cache keys ("Petrov Aleksandr" == "Aleksandr Petrov").
     */
    public String canonicalKey() {
        return String.join(" ", tokens.stream().sorted().toList());
    }
}
