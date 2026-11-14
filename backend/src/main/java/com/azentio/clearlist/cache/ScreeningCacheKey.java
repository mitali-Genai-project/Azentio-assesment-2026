package com.azentio.clearlist.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Cache key = watchlist version + order-insensitive normalised name.
 *
 * <p>DOB and nationality are not part of the key because matching is name-only; if they ever influence
 * scoring they must be added here. The name is SHA-256 hashed in the external key so customer names are
 * never stored in plain text in Redis.
 */
public record ScreeningCacheKey(long versionId, String canonicalName) {

    private static final String PREFIX = "clearlist:screening:v";

    public String externalKey() {
        return PREFIX + versionId + ":" + sha256(canonicalName);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}
