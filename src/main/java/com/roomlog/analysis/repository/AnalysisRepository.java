package com.roomlog.analysis.repository;

import com.roomlog.analysis.domain.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    List<Analysis> findByRoomId(Long roomId);

    List<Analysis> findByRoomIdIn(List<Long> roomIds);

    Optional<Analysis> findFirstByRoomIdOrderByCreatedAtDesc(Long roomId);

    Optional<Analysis> findFirstByInScanIdAndStatusOrderByCreatedAtDesc(Long inScanId, Analysis.Status status);

    List<Analysis> findByRoomIdInAndOutScanIdIsNotNullOrderByCreatedAtDesc(List<Long> roomIds);
    boolean existsByRoomIdAndStatusAndCreatedAtAfter(Long roomId, Analysis.Status status, LocalDateTime after);
}
