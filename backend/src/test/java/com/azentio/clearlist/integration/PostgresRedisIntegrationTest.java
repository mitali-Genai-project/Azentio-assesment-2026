package com.azentio.clearlist.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.azentio.clearlist.cache.ScreeningCacheKey;
import com.azentio.clearlist.dto.ScreeningRequest;
import com.azentio.clearlist.dto.ScreeningResponse;
import com.azentio.clearlist.matcher.NameNormalizer;
import com.azentio.clearlist.service.ScreeningService;
import com.azentio.clearlist.service.WatchlistService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Requires the docker-compose PostgreSQL and Redis. Excluded from the default build; run with -Pintegration.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
class PostgresRedisIntegrationTest {

    private static final String HEADER = "entryId,primaryName,aliases,dob,nationality,listType\n";

    @Autowired
    private WatchlistService watchlistService;
    @Autowired
    private ScreeningService screeningService;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private NameNormalizer normalizer;

    @Test
    void redisCacheIsVersionScopedAndEveryRequestIsPersisted() {
        long v1 = upload(HEADER + "WL-1,Aleksandr Petrov,,1980-01-01,RU,SANCTIONS\n");
        ScreeningResponse first = screen("Aleksandr Petrov");
        ScreeningResponse second = screen("Petrov, Aleksandr");

        assertThat(first.cacheHit()).isFalse();
        assertThat(second.cacheHit()).isTrue();
        assertThat(second.screeningId()).isNotEqualTo(first.screeningId());
        String redisKey = new ScreeningCacheKey(v1, normalizer.normalize("Aleksandr Petrov").canonicalKey()).externalKey();
        assertThat(redis.getExpire(redisKey)).isPositive();

        long v2 = upload(HEADER + "WL-2,Viktor Orlov,,,RU,PEP\n");
        ScreeningResponse afterUpload = screen("Aleksandr Petrov");
        assertThat(afterUpload.listVersion()).isEqualTo(v2);
        assertThat(afterUpload.cacheHit()).isFalse();
        assertThat(afterUpload.decision().name()).isEqualTo("CLEAR");
    }

    /** Prints the PostgreSQL plan for the review-queue query so it can be recorded in the README. */
    @Test
    void reviewQueueQueryUsesStatusCreatedAtIndex() {
        jdbc.execute("ANALYZE screening_hit");
        jdbc.execute("SET enable_seqscan = off"); // small test tables would otherwise always seq-scan
        List<String> plan = jdbc.queryForList(
                "EXPLAIN SELECT * FROM screening_hit WHERE status = 'OPEN' ORDER BY created_at DESC LIMIT 20",
                String.class);
        plan.forEach(System.out::println);
        assertThat(String.join("\n", plan)).contains("idx_screening_hit_status_created_at");
    }

    private long upload(String csv) {
        return watchlistService.upload("list.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8), "it")
                .versionId();
    }

    private ScreeningResponse screen(String name) {
        return screeningService.screen(new ScreeningRequest("CUST-IT", name, null, null));
    }
}
