package com.roomlog.defect.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 하자 종류·심각도별 ㎡당 수리 단가.
 * 심각할수록 수리 공정이 늘어나는 방식으로 금액이 달라진다(배율을 곱하지 않는다).
 * 단가는 건설공사 표준시장단가의 공종 단가에 주재료비(시중 판매가 환산)를 더한 값이며, 근거는 basis에 남긴다.
 */
@Entity
@Table(name = "repair_unit_price",
        uniqueConstraints = @UniqueConstraint(name = "uk_repair_unit_price", columnNames = {"defect_type", "severity"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RepairUnitPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "repair_unit_price_id")
    private Long id;

    @Column(name = "defect_type", nullable = false, length = 30)
    private String defectType;

    @Column(nullable = false, length = 10)
    private String severity;

    /** ㎡당 단가(원). */
    @Column(name = "unit_price", nullable = false)
    private Integer unitPrice;

    /** 단가 산출 근거. 공종 코드와 단가, 재료비 출처를 적는다. */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String basis;

    @Builder
    public RepairUnitPrice(String defectType, String severity, Integer unitPrice, String basis) {
        this.defectType = defectType;
        this.severity = severity;
        this.unitPrice = unitPrice;
        this.basis = basis;
    }
}
