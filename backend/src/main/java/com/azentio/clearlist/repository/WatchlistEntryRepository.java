package com.azentio.clearlist.repository;

import com.azentio.clearlist.entity.WatchlistEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchlistEntryRepository extends JpaRepository<WatchlistEntry, Long> {

    @Query("select distinct e from WatchlistEntry e left join fetch e.aliases where e.version.id = :versionId")
    List<WatchlistEntry> findAllWithAliasesByVersionId(@Param("versionId") long versionId);
}
