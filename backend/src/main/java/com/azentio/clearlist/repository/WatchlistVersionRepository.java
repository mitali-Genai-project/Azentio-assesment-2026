package com.azentio.clearlist.repository;

import com.azentio.clearlist.entity.WatchlistVersion;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistVersionRepository extends JpaRepository<WatchlistVersion, Long> {

    /** The current version is simply the most recently loaded one. */
    Optional<WatchlistVersion> findTopByOrderByIdDesc();
}
