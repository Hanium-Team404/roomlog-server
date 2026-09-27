package com.roomlog.defect.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 하자 종류별 기본 출장비. 수리에 필요한 직종의 반나절(0.5일) 시중노임단가다.
 * 업체가 한 번 방문해 여러 하자를 함께 고치므로, 출장비는 하자마다가 아니라 총액에 한 번만 더한다.
 */
@Entity
@Table(name = "repair_visit_fee")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RepairVisitFee {

    @Id
    @Column(name = "defect_type", length = 30)
    private String defectType;

    /** 수리 직종(도배공, 도장공, 내장공 등). */
    @Column(nullable = false, length = 20)
    private String trade;

    @Column(nullable = false)
    private Integer fee;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String basis;

    @Builder
    public RepairVisitFee(String defectType, String trade, Integer fee, String basis) {
        this.defectType = defectType;
        this.trade = trade;
        this.fee = fee;
        this.basis = basis;
    }
}
