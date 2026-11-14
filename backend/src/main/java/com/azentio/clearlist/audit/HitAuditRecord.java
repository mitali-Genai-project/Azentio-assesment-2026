package com.azentio.clearlist.audit;

import com.azentio.clearlist.entity.HitStatus;
import com.azentio.clearlist.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * One successful hit disposition. Append-only, enforced three ways at the application layer:
 * no setters, Hibernate {@code @Immutable} (no UPDATE is ever generated), and lifecycle callbacks that
 * reject updates/removals. {@link HitAuditRepository} also exposes no update or delete methods.
 */
@Entity
@Immutable
@Table(name = "hit_audit_record")
public class HitAuditRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hit_id", nullable = false, updatable = false)
    private UUID hitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, updatable = false)
    private HitStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    private HitStatus toStatus;

    @Column(name = "actor", nullable = false, updatable = false)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", nullable = false, updatable = false)
    private Role role;

    @Column(name = "reason", nullable = false, updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HitAuditRecord() {
    }

    public HitAuditRecord(UUID hitId, HitStatus fromStatus, HitStatus toStatus, String actor, Role role,
                          String reason, Instant createdAt) {
        this.hitId = hitId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.role = role;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    @PreUpdate
    @PreRemove
    void rejectModification() {
        throw new UnsupportedOperationException("Audit records are append-only");
    }

    public Long getId() {
        return id;
    }

    public UUID getHitId() {
        return hitId;
    }

    public HitStatus getFromStatus() {
        return fromStatus;
    }

    public HitStatus getToStatus() {
        return toStatus;
    }

    public String getActor() {
        return actor;
    }

    public Role getRole() {
        return role;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
