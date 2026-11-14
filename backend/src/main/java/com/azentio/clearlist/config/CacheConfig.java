package com.azentio.clearlist.config;

import com.azentio.clearlist.cache.InMemoryScreeningCache;
import com.azentio.clearlist.cache.NoOpScreeningCache;
import com.azentio.clearlist.cache.RedisScreeningCache;
import com.azentio.clearlist.cache.ScreeningCache;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class CacheConfig {

    @Bean
    public ScreeningCache screeningCache(CacheProperties properties, ObjectProvider<StringRedisTemplate> redis,
                                         ObjectMapper objectMapper, Clock clock) {
        return switch (properties.type()) {
            case REDIS -> new RedisScreeningCache(redis.getObject(), objectMapper, properties.ttl());
            case MEMORY -> new InMemoryScreeningCache(properties.ttl(), clock);
            case NONE -> new NoOpScreeningCache();
        };
    }
}
