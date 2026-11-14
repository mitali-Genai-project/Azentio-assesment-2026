package com.azentio.clearlist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A watchlist entry that scored above the threshold for a screening. Only {@link #status} changes after
 * creation; {@link #rowVersion} provides optimistic locking so two reviewers cannot both move the same hit.
 */
@Entity
@Table(name = "screening_hit")
public class ScreeningHit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "screening_id", nullable = false, updatable = false)
    private Screening screening;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "watchlist_entry_id", nullable = false, updatable = false)
    private WatchlistEntry watchlistEntry;

    @Column(name = "matched_name", nullable = false, updatable = false)
    private String matchedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "matched_on", nullable = false, updatable = false)
    private MatchedOn matchedOn;

    @Column(name = "score", nullable = false, updatable = false)
    private double score;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private HitStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected ScreeningHit() {
    }

    public ScreeningHit(Screening screening, WatchlistEntry watchlistEntry, String matchedName,
                        MatchedOn matchedOn, double score, Instant createdAt) {
        this.screening = screening;
        this.watchlistEntry = watchlistEntry;
        this.matchedName = matchedName;
        this.matchedOn = matchedOn;
        this.score = score;
        this.status = HitStatus.OPEN;
        this.createdAt = createdAt;
    }

    /** Status change only; who may do it is decided by HitTransitionPolicy before this is called. */
    public void transitionTo(HitStatus newStatus) {
        this.status = newStatus;
    }

    public UUID getId() {
        return id;
    }

    public Screening getScreening() {
        return screening;
    }

    public WatchlistEntry getWatchlistEntry() {
        return watchlistEntry;
    }

    public String getMatchedName() {
        return matchedName;
    }

    public MatchedOn getMatchedOn() {
        return matchedOn;
    }

    public double getScore() {
        return score;
    }

    public HitStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
