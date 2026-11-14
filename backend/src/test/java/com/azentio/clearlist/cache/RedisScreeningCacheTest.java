package com.azentio.clearlist.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azentio.clearlist.entity.ListType;
import com.azentio.clearlist.entity.MatchedOn;
import com.azentio.clearlist.matcher.MatchCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** Offline unit test with a mocked Redis template; a real-Redis test lives in the integration profile. */
class RedisScreeningCacheTest {

    private static final List<MatchCandidate> MATCHES = List.of(
            new MatchCandidate(1L, "WL-1", "Sasha Petrov", MatchedOn.ALIAS, 0.95, ListType.PEP));

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedisScreeningCache cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        cache = new RedisScreeningCache(redis, objectMapper, Duration.ofMinutes(5));
    }

    @Test
    void writesJsonWithTtlAndReadsItBack() {
        ScreeningCacheKey key = new ScreeningCacheKey(3, "petruv sasha");
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);

        cache.put(key, MATCHES);
        verify(values).set(eq(key.externalKey()), json.capture(), eq(Duration.ofMinutes(5)));

        when(values.get(key.externalKey())).thenReturn(json.getValue());
        assertThat(cache.get(key)).contains(MATCHES);
    }

    @Test
    void ignoresValueWhoseEmbeddedVersionDiffersFromKey() throws Exception {
        String staleValue = objectMapper.writeValueAsString(new RedisScreeningCache.CachedValue(1, MATCHES));
        when(values.get(anyString())).thenReturn(staleValue);

        assertThat(cache.get(new ScreeningCacheKey(2, "petruv sasha"))).isEmpty();
    }

    @Test
    void failsOpenWhenRedisIsUnavailable() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(cache.get(new ScreeningCacheKey(1, "x y"))).isEmpty();
    }

    @Test
    void treatsCorruptValueAsMiss() {
        when(values.get(anyString())).thenReturn("{not json");

        assertThat(cache.get(new ScreeningCacheKey(1, "x y"))).isEmpty();
    }
}
