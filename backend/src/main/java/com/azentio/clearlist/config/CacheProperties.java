package com.azentio.clearlist.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param type redis (runtime), memory (offline tests / local), none (caching disabled)
 * @param ttl  how long a matching result stays cached, e.g. PT10M
 */
@Validated
@ConfigurationProperties(prefix = "clearlist.cache")
public record CacheProperties(@NotNull Type type, @NotNull Duration ttl) {

    public enum Type { REDIS, MEMORY, NONE }
}
