package com.azentio.clearlist.dto;

import com.azentio.clearlist.entity.HitStatus;
import com.azentio.clearlist.security.Role;
import java.time.Instant;
import java.util.UUID;

public record AuditRecordResponse(
        long auditId,
        UUID hitId,
        HitStatus fromStatus,
        HitStatus toStatus,
        String actor,
        Role role,
        String reason,
        Instant timestamp) {
}
