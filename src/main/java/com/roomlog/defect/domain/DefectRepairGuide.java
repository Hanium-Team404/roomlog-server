package com.roomlog.defect.domain;

import com.roomlog.defect.dto.RepairItem;
import com.roomlog.defect.dto.RepairVideo;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "defect_repair_guide")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DefectRepairGuide {

    @Id
    @Column(name = "defect_id")
    private Long defectId;

    @Column(name = "self_repair_possible", nullable = false)
    private boolean selfRepairPossible;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Convert(converter = RepairVideoListConverter.class)
    @Column(name = "videos", columnDefinition = "TEXT")
    private List<RepairVideo> videos;

    /** 영상 검색에 쓴 검색어. 영상을 못 받았을 때 다시 검색하는 데 쓴다. */
    @Column(name = "video_search_query")
    private String videoSearchQuery;

    @Convert(converter = RepairItemListConverter.class)
    @Column(name = "items", columnDefinition = "TEXT")
    private List<RepairItem> items;

    @Column(name = "total_cost", nullable = false)
    private Integer totalCost;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public boolean hasVideo() {
        return videos != null && !videos.isEmpty();
    }

    /** 검색어는 남아 있는데 영상만 못 받았던 경우, 나중에 다시 검색해 채워 넣는다. */
    public void updateVideos(List<RepairVideo> videos) {
        this.videos = videos;
    }

    /** 준비물 이미지가 나중에 채워졌을 때 저장된 목록을 새로 바꾼다. */
    public void updateItems(List<RepairItem> items) {
        this.items = items;
    }

    @Builder
    public DefectRepairGuide(Long defectId, boolean selfRepairPossible, String description,
                             List<RepairVideo> videos, String videoSearchQuery,
                             List<RepairItem> items, Integer totalCost) {
        this.defectId = defectId;
        this.selfRepairPossible = selfRepairPossible;
        this.description = description;
        this.videos = videos;
        this.videoSearchQuery = videoSearchQuery;
        this.items = items;
        this.totalCost = totalCost != null ? totalCost : 0;
    }
}
