package com.azentio.clearlist.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Immutable;

/** A person on a specific watchlist version. Immutable once written. */
@Entity
@Immutable
@Table(name = "watchlist_entry")
public class WatchlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "watchlist_entry_seq")
    @SequenceGenerator(name = "watchlist_entry_seq", sequenceName = "watchlist_entry_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private WatchlistVersion version;

    /** The list publisher's identifier (e.g. "WL-00042"); unique within a version. */
    @Column(name = "entry_ref", nullable = false)
    private String entryRef;

    @Column(name = "primary_name", nullable = false)
    private String primaryName;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "nationality", length = 2)
    private String nationality;

    @Enumerated(EnumType.STRING)
    @Column(name = "list_type", nullable = false)
    private ListType listType;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "watchlist_entry_alias", joinColumns = @JoinColumn(name = "entry_id"))
    @Column(name = "alias", nullable = false)
    private List<String> aliases = new ArrayList<>();

    protected WatchlistEntry() {
    }

    public WatchlistEntry(WatchlistVersion version, String entryRef, String primaryName, List<String> aliases,
                          LocalDate dob, String nationality, ListType listType) {
        this.version = version;
        this.entryRef = entryRef;
        this.primaryName = primaryName;
        this.aliases = new ArrayList<>(aliases);
        this.dob = dob;
        this.nationality = nationality;
        this.listType = listType;
    }

    public Long getId() {
        return id;
    }

    public WatchlistVersion getVersion() {
        return version;
    }

    public String getEntryRef() {
        return entryRef;
    }

    public String getPrimaryName() {
        return primaryName;
    }

    public List<String> getAliases() {
        return List.copyOf(aliases);
    }

    public LocalDate getDob() {
        return dob;
    }

    public String getNationality() {
        return nationality;
    }

    public ListType getListType() {
        return listType;
    }
}
