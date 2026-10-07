package com.roomlog.defect.repository;

import com.roomlog.defect.domain.RepairSupply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RepairSupplyRepository extends JpaRepository<RepairSupply, Long> {

    List<RepairSupply> findByDefectTypeOrderBySortOrderAsc(String defectType);

    List<RepairSupply> findByImageUrlIsNull();

    List<RepairSupply> findByNameIn(Collection<String> names);
}
