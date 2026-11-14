package com.azentio.clearlist.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Matching tunables, bound from {@code clearlist.matching.*}.
 *
 * @param threshold      minimum overall name score (0..1) for a watchlist entry to become a hit
 * @param tokenThreshold minimum Jaro-Winkler similarity for two name tokens to count as the same token;
 *                       weaker token pairs contribute nothing, which stops a strong surname match from
 *                       carrying an unrelated given name over the threshold
 * @param maxHits        maximum number of hits returned in a screening response (all hits are persisted)
 */
@Validated
@ConfigurationProperties(prefix = "clearlist.matching")
public record MatchingProperties(
        @DecimalMin("0.0") @DecimalMax("1.0") double threshold,
        @DecimalMin("0.0") @DecimalMax("1.0") double tokenThreshold,
        @Min(1) @Max(100) int maxHits) {
}
