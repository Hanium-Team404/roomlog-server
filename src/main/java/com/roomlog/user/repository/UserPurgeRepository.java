package com.roomlog.user.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 회원 탈퇴 시 한 사용자의 데이터를 DB에서 실제로 지운다.
 * JPA 엔티티는 @SQLRestriction(is_deleted = false) 때문에 soft delete된 행이 보이지 않으므로
 * 이미 삭제 표시된 행까지 남김없이 지우기 위해 네이티브 SQL을 쓴다.
 */
@Repository
@RequiredArgsConstructor
public class UserPurgeRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** 사용자가 올린 모든 스캔 ID (soft delete된 것 포함). */
    public List<Long> findScanIds(Long userId) {
        return jdbc.queryForList("SELECT scan_id FROM scan WHERE user_id = :userId",
                Map.of("userId", userId), Long.class);
    }

    /** 사용자의 모든 하자 이미지 URL (soft delete된 것 포함, 중복 제거). */
    public List<String> findDefectImageUrls(Long userId) {
        return jdbc.queryForList("""
                SELECT DISTINCT d.image_url
                FROM defect d
                JOIN analysis a ON a.analysis_id = d.analysis_id
                JOIN room r ON r.room_id = a.room_id
                JOIN house h ON h.house_id = r.house_id
                WHERE h.user_id = :userId AND d.image_url IS NOT NULL
                """, Map.of("userId", userId), String.class);
    }

    /** 사용자 행과 그에 딸린 모든 데이터를 하위 테이블부터 차례로 지운다. */
    @Transactional
    public void deleteAllUserData(Long userId) {
        Map<String, Object> params = Map.of("userId", userId);

        jdbc.update("DELETE FROM refresh_token WHERE user_id = :userId", params);

        jdbc.update("""
                DELETE FROM chat_message
                WHERE chat_session_id IN (SELECT chat_session_id FROM chat_session WHERE user_id = :userId)
                """, params);
        jdbc.update("DELETE FROM chat_session WHERE user_id = :userId", params);

        // 하자 하위: 자가 수리 가이드, 견적·수리 기록 연결
        jdbc.update("""
                DELETE FROM defect_repair_guide
                WHERE defect_id IN (
                    SELECT d.defect_id FROM defect d
                    JOIN analysis a ON a.analysis_id = d.analysis_id
                    JOIN room r ON r.room_id = a.room_id
                    JOIN house h ON h.house_id = r.house_id
                    WHERE h.user_id = :userId)
                """, params);
        jdbc.update("""
                DELETE FROM estimate_defect
                WHERE estimate_id IN (SELECT estimate_id FROM estimate WHERE user_id = :userId)
                """, params);
        jdbc.update("""
                DELETE FROM repair_defect
                WHERE repair_id IN (
                    SELECT rp.repair_id FROM repair rp
                    JOIN room r ON r.room_id = rp.room_id
                    JOIN house h ON h.house_id = r.house_id
                    WHERE h.user_id = :userId)
                """, params);
        jdbc.update("""
                DELETE FROM repair
                WHERE room_id IN (
                    SELECT r.room_id FROM room r
                    JOIN house h ON h.house_id = r.house_id
                    WHERE h.user_id = :userId)
                """, params);
        jdbc.update("DELETE FROM estimate WHERE user_id = :userId", params);

        jdbc.update("""
                DELETE FROM defect
                WHERE analysis_id IN (
                    SELECT a.analysis_id FROM analysis a
                    JOIN room r ON r.room_id = a.room_id
                    JOIN house h ON h.house_id = r.house_id
                    WHERE h.user_id = :userId)
                """, params);
        jdbc.update("""
                DELETE FROM analysis
                WHERE room_id IN (
                    SELECT r.room_id FROM room r
                    JOIN house h ON h.house_id = r.house_id
                    WHERE h.user_id = :userId)
                """, params);
        jdbc.update("DELETE FROM scan WHERE user_id = :userId", params);
        jdbc.update("""
                DELETE FROM room
                WHERE house_id IN (SELECT house_id FROM house WHERE user_id = :userId)
                """, params);
        jdbc.update("DELETE FROM house WHERE user_id = :userId", params);

        jdbc.update("DELETE FROM user WHERE user_id = :userId", params);
    }
}
