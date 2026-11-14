package com.azentio.clearlist.repository;

import com.azentio.clearlist.entity.Screening;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreeningRepository extends JpaRepository<Screening, UUID> {
}
