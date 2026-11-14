package com.azentio.clearlist.audit;

import com.azentio.clearlist.dto.AuditRecordResponse;
import com.azentio.clearlist.entity.HitStatus;
import com.azentio.clearlist.exception.ResourceNotFoundException;
import com.azentio.clearlist.repository.ScreeningHitRepository;
import com.azentio.clearlist.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final HitAuditRepository auditRepository;
    private final ScreeningHitRepository hitRepository;
    private final Clock clock;

    public AuditService(HitAuditRepository auditRepository, ScreeningHitRepository hitRepository, Clock clock) {
        this.auditRepository = auditRepository;
        this.hitRepository = hitRepository;
        this.clock = clock;
    }

    /**
     * Must run inside the caller's transaction so the status change and its audit record commit or roll
     * back together - a disposition can never exist without its audit entry.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordDisposition(UUID hitId, HitStatus from, HitStatus to, String actor, Role role, String reason) {
        auditRepository.save(new HitAuditRecord(hitId, from, to, actor, role, reason, Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public List<AuditRecordResponse> history(UUID hitId) {
        if (!hitRepository.existsById(hitId)) {
            throw new ResourceNotFoundException("Hit " + hitId + " not found");
        }
        return auditRepository.findByHitIdOrderByCreatedAtAscIdAsc(hitId).stream()
                .map(r -> new AuditRecordResponse(r.getId(), r.getHitId(), r.getFromStatus(), r.getToStatus(),
                        r.getActor(), r.getRole(), r.getReason(), r.getCreatedAt()))
                .toList();
    }
}
