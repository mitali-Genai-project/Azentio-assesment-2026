package com.azentio.clearlist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/** One uploaded watchlist. Immutable: Hibernate never issues UPDATEs for it. */
@Entity
@Immutable
@Table(name = "watchlist_version")
public class WatchlistVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_filename")
    private String sourceFilename;

    @Column(name = "entry_count", nullable = false)
    private int entryCount;

    @Column(name = "loaded_at", nullable = false)
    private Instant loadedAt;

    @Column(name = "loaded_by", nullable = false)
    private String loadedBy;

    protected WatchlistVersion() {
    }

    public WatchlistVersion(String sourceFilename, int entryCount, Instant loadedAt, String loadedBy) {
        this.sourceFilename = sourceFilename;
        this.entryCount = entryCount;
        this.loadedAt = loadedAt;
        this.loadedBy = loadedBy;
    }

    public Long getId() {
        return id;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public int getEntryCount() {
        return entryCount;
    }

    public Instant getLoadedAt() {
        return loadedAt;
    }

    public String getLoadedBy() {
        return loadedBy;
    }
}
