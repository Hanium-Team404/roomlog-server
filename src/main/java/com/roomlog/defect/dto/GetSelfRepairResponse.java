package com.roomlog.defect.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roomlog.defect.domain.DefectRepairGuide;
import lombok.Getter;

import java.util.List;

@Getter
public class GetSelfRepairResponse {

    @JsonProperty("defect_id")
    private final Long defectId;

    @JsonProperty("self_repair_possible")
    private final boolean selfRepairPossible;

    private final String description;

    /** 수리 방법 영상. 자가 수리 불가이거나 검색 결과가 없으면 빈 배열. */
    private final List<RepairVideo> videos;

    /** 준비물 목록. 없으면 빈 배열. */
    private final List<RepairItem> items;

    private GetSelfRepairResponse(DefectRepairGuide guide) {
        this.defectId = guide.getDefectId();
        this.selfRepairPossible = guide.isSelfRepairPossible();
        this.description = guide.getDescription();
        this.videos = guide.getVideos() != null ? guide.getVideos() : List.of();
        this.items = guide.getItems() != null ? guide.getItems() : List.of();
    }

    public static GetSelfRepairResponse from(DefectRepairGuide guide) {
        return new GetSelfRepairResponse(guide);
    }
}
