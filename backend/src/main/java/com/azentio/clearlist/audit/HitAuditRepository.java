package com.azentio.clearlist.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Deliberately extends the bare {@link Repository} marker instead of JpaRepository: only insert and read
 * are available, so no application code can update or delete audit history.
 */
public interface HitAuditRepository extends Repository<HitAuditRecord, Long> {

    HitAuditRecord save(HitAuditRecord record);

    List<HitAuditRecord> findByHitIdOrderByCreatedAtAscIdAsc(UUID hitId);
}
