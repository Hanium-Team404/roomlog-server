package com.roomlog.defect.domain;

import com.roomlog.defect.dto.RegionPoint;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.List;

@SQLRestriction("is_deleted = false")
@Entity
@Table(name = "defect")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Defect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "defect_id")
    private Long id;

    @Column(name = "analysis_id", nullable = false)
    private Long analysisId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String severity;

    @Column(nullable = false)
    private String location;

    /** 하자 면적(㎠). AI가 준 값 그대로 저장하고 앱에도 ㎠로 내려준다. */
    @Column(nullable = false)
    private Float area;

    @Column(name = "estimated_cost", nullable = false)
    private Integer estimatedCost;

    @Column
    private String description;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Convert(converter = RegionPointListConverter.class)
    @Column(name = "region_3d", columnDefinition = "TEXT")
    private List<RegionPoint> region3d;

    @Column(name = "is_deleted", nullable = false, columnDefinition = "BIT(1) DEFAULT 0")
    private boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    @Builder
    public Defect(Long analysisId, String type, String severity, String location, Float area,
                  Integer estimatedCost, String description, String imageUrl, List<RegionPoint> region3d) {
        this.analysisId = analysisId;
        this.type = type;
        this.severity = severity;
        this.location = location;
        this.area = area;
        this.estimatedCost = estimatedCost;
        this.description = description;
        this.imageUrl = imageUrl;
        this.region3d = region3d;
    }
}
