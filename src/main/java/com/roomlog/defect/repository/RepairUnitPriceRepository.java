package com.roomlog.defect.repository;

import com.roomlog.defect.domain.RepairUnitPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RepairUnitPriceRepository extends JpaRepository<RepairUnitPrice, Long> {

    Optional<RepairUnitPrice> findByDefectTypeAndSeverity(String defectType, String severity);
}
