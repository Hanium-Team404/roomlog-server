package com.roomlog.defect.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roomlog.defect.domain.Defect;
import lombok.Getter;

import java.util.List;

@Getter
public class DefectItemResponse {

    @JsonProperty("defect_id")
    private final Long defectId;

    @JsonProperty("analysis_id")
    private final Long analysisId;

    private final String type;

    private final String location;

    private final String severity;

    private final Float area;

    @JsonProperty("estimated_cost")
    private final Integer estimatedCost;

    private final String description;

    @JsonProperty("image_url")
    private final String imageUrl;

    @JsonProperty("region_3d")
    private final List<RegionPoint> region3d;

    protected DefectItemResponse(Defect defect) {
        this.defectId = defect.getId();
        this.analysisId = defect.getAnalysisId();
        this.type = defect.getType();
        this.location = defect.getLocation();
        this.severity = defect.getSeverity();
        this.area = defect.getArea();
        this.estimatedCost = defect.getEstimatedCost();
        this.description = defect.getDescription();
        this.imageUrl = defect.getImageUrl();
        this.region3d = defect.getRegion3d();
    }

    public static DefectItemResponse from(Defect defect) {
        return new DefectItemResponse(defect);
    }
}
