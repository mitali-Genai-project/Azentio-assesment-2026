package com.azentio.clearlist.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.azentio.clearlist.dto.DispositionRequest;
import com.azentio.clearlist.dto.ScreeningRequest;
import com.azentio.clearlist.entity.HitStatus;
import com.azentio.clearlist.security.Role;
import com.azentio.clearlist.service.DispositionService;
import com.azentio.clearlist.service.ScreeningService;
import com.azentio.clearlist.service.WatchlistService;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class AuditAppendOnlyTest {

    @Autowired
    private WatchlistService watchlistService;
    @Autowired
    private ScreeningService screeningService;
    @Autowired
    private DispositionService dispositionService;
    @Autowired
    private HitAuditRepository auditRepository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void repositoryExposesOnlyInsertAndRead() {
        assertThat(Arrays.stream(HitAuditRepository.class.getMethods()).map(Method::getName))
                .containsExactlyInAnyOrder("save", "findByHitIdOrderByCreatedAtAscIdAsc");
    }

    @Test
    void persistedAuditRecordCannotBeDeletedOrChanged() {
        UUID hitId = createHitAndEscalateIt();
        assertThat(auditRepository.findByHitIdOrderByCreatedAtAscIdAsc(hitId)).hasSize(1);

        // Attempted delete through JPA is refused by the entity's lifecycle callback
        Throwable deleteFailure = attempt(() -> transactionTemplate.executeWithoutResult(tx -> {
            HitAuditRecord record = auditRepository.findByHitIdOrderByCreatedAtAscIdAsc(hitId).get(0);
            entityManager.remove(record);
            entityManager.flush();
        }));
        assertThat(causeChain(deleteFailure)).anyMatch(UnsupportedOperationException.class::isInstance);

        // @Immutable: field changes on a managed record are never written back
        transactionTemplate.executeWithoutResult(tx -> {
            HitAuditRecord record = auditRepository.findByHitIdOrderByCreatedAtAscIdAsc(hitId).get(0);
            org.springframework.test.util.ReflectionTestUtils.setField(record, "reason", "tampered");
        });
        entityManager.clear();

        List<HitAuditRecord> after = auditRepository.findByHitIdOrderByCreatedAtAscIdAsc(hitId);
        assertThat(after).singleElement().satisfies(r -> {
            assertThat(r.getReason()).isEqualTo("Needs supervisor review");
            assertThat(r.getToStatus()).isEqualTo(HitStatus.ESCALATED);
        });
    }

    private UUID createHitAndEscalateIt() {
        String csv = "entryId,primaryName,aliases,dob,nationality,listType\nWL-A,Viktor Orlov,,,RU,PEP\n";
        watchlistService.upload("list.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8), "test");
        UUID hitId = screeningService.screen(new ScreeningRequest("CUST-AUDIT", "Viktor Orlov", null, null))
                .topHits().get(0).hitId();
        dispositionService.dispose(hitId, new DispositionRequest(HitStatus.ESCALATED, "Needs supervisor review"),
                "analyst", Set.of(Role.ANALYST));
        return hitId;
    }

    private static Throwable attempt(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    private static List<Throwable> causeChain(Throwable t) {
        List<Throwable> chain = new java.util.ArrayList<>();
        for (Throwable c = t; c != null && !chain.contains(c); c = c.getCause()) {
            chain.add(c);
        }
        return chain;
    }
}
