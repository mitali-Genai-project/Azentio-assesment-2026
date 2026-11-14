package com.azentio.clearlist.cache;

import com.azentio.clearlist.matcher.MatchCandidate;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis-backed cache. Values are JSON and carry their watchlist version, which is re-checked on read
 * (defence in depth on top of the version-scoped key).
 *
 * <p>Fails open: if Redis is unavailable or returns something unreadable, the call is treated as a miss
 * and the screening is computed normally. A cache outage must never stop screening.
 */
public class RedisScreeningCache implements ScreeningCache {

    private static final Logger log = LoggerFactory.getLogger(RedisScreeningCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisScreeningCache(StringRedisTemplate redis, ObjectMapper objectMapper, Duration ttl) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    @Override
    public Optional<List<MatchCandidate>> get(ScreeningCacheKey key) {
        try {
            String json = redis.opsForValue().get(key.externalKey());
            if (json == null) {
                return Optional.empty();
            }
            CachedValue value = objectMapper.readValue(json, CachedValue.class);
            if (value.versionId() != key.versionId()) {
                log.warn("Ignoring cached screening result for version {} under key for version {}",
                        value.versionId(), key.versionId());
                return Optional.empty();
            }
            return Optional.of(value.matches());
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Screening cache read failed; computing instead: {}", e.toString());
            return Optional.empty();
        }
    }

    @Override
    public void put(ScreeningCacheKey key, List<MatchCandidate> matches) {
        try {
            String json = objectMapper.writeValueAsString(new CachedValue(key.versionId(), matches));
            redis.opsForValue().set(key.externalKey(), json, ttl);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Screening cache write failed: {}", e.toString());
        }
    }

    record CachedValue(long versionId, List<MatchCandidate> matches) {
    }
}
