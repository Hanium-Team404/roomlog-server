package com.roomlog.defect.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 하자 종류별 자가 수리 준비물.
 * 쇼핑 검색 API로 상품을 가져올 수 없어(네이버 쇼핑 검색 종료, 쿠팡은 공개 API 없음)
 * 하자 종류가 5가지로 한정적이라는 점을 이용해 직접 관리한다.
 * 상품을 바꾸려면 이 테이블의 행만 수정하면 되고, 재배포는 필요 없다.
 */
@Entity
@Table(name = "repair_supply", indexes = @Index(name = "idx_repair_supply_type", columnList = "defect_type"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RepairSupply {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "repair_supply_id")
    private Long id;

    @Column(name = "defect_type", nullable = false, length = 30)
    private String defectType;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer price;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "purchase_url", columnDefinition = "TEXT", nullable = false)
    private String purchaseUrl;

    /** 화면에 보여줄 순서. 작을수록 먼저. */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Builder
    public RepairSupply(String defectType, String name, Integer price,
                        String imageUrl, String purchaseUrl, Integer sortOrder) {
        this.defectType = defectType;
        this.name = name;
        this.price = price;
        this.imageUrl = imageUrl;
        this.purchaseUrl = purchaseUrl;
        this.sortOrder = sortOrder != null ? sortOrder : 0;
    }

    public void updateImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
