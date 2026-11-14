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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** One screening request and its outcome, tied to the exact watchlist version that was used. */
@Entity
@Immutable
@Table(name = "screening")
public class Screening {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_ref", nullable = false)
    private String customerRef;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "nationality", length = 2)
    private String nationality;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "watchlist_version_id", nullable = false)
    private WatchlistVersion watchlistVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false)
    private Decision decision;

    /** True when the match result came from the screening cache rather than a fresh computation. */
    @Column(name = "cache_hit", nullable = false)
    private boolean cacheHit;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Screening() {
    }

    public Screening(String customerRef, String fullName, LocalDate dob, String nationality,
                     WatchlistVersion watchlistVersion, Decision decision, boolean cacheHit, Instant createdAt) {
        this.customerRef = customerRef;
        this.fullName = fullName;
        this.dob = dob;
        this.nationality = nationality;
        this.watchlistVersion = watchlistVersion;
        this.decision = decision;
        this.cacheHit = cacheHit;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerRef() {
        return customerRef;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getDob() {
        return dob;
    }

    public String getNationality() {
        return nationality;
    }

    public WatchlistVersion getWatchlistVersion() {
        return watchlistVersion;
    }

    public Decision getDecision() {
        return decision;
    }

    public boolean isCacheHit() {
        return cacheHit;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
