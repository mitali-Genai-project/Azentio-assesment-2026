package com.azentio.clearlist.repository;

import com.azentio.clearlist.entity.HitStatus;
import com.azentio.clearlist.entity.ScreeningHit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreeningHitRepository extends JpaRepository<ScreeningHit, UUID> {

    /** Review queue. Served by index (status, created_at DESC); the graph avoids N+1 lookups per row. */
    @EntityGraph(attributePaths = {"screening", "watchlistEntry"})
    Page<ScreeningHit> findByStatus(HitStatus status, Pageable pageable);

    List<ScreeningHit> findByScreeningId(UUID screeningId);
}
