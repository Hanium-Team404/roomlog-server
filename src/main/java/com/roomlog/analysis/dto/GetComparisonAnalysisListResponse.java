package com.roomlog.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roomlog.analysis.domain.Analysis;
import com.roomlog.defect.dto.DefectItemResponse;
import com.roomlog.room.domain.Room;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;


@Getter
public class GetComparisonAnalysisListResponse {

    @JsonProperty("analysis_id")
    private final Long analysisId;

    private final String status;

    @JsonProperty("created_at")
    private final LocalDateTime createdAt;

    @JsonProperty("in_room")
    private final RoomInfo inRoom;

    @JsonProperty("out_room")
    private final RoomInfo outRoom;

    private final Summary summary;

    private GetComparisonAnalysisListResponse(Analysis analysis, Room inRoom, Room outRoom, List<DefectItemResponse> defects) {
        this.analysisId = analysis.getId();
        this.status = analysis.getStatus().name();
        this.createdAt = analysis.getCreatedAt();
        this.inRoom = RoomInfo.from(inRoom);
        this.outRoom = RoomInfo.from(outRoom);
        this.summary = Summary.from(analysis, defects);
    }

    public static GetComparisonAnalysisListResponse of(Analysis analysis, Room inRoom, Room outRoom, List<DefectItemResponse> defects) {
        return new GetComparisonAnalysisListResponse(analysis, inRoom, outRoom, defects);
    }

    @Getter
    public static class RoomInfo {

        @JsonProperty("room_id")
        private final Long roomId;

        private final String name;

        @JsonProperty("thumbnail_url")
        private final String thumbnailUrl;

        private RoomInfo(Room room) {
            this.roomId = room.getId();
            this.name = room.getName();
            this.thumbnailUrl = room.getThumbnailUrl();
        }

        public static RoomInfo from(Room room) {
            return new RoomInfo(room);
        }
    }

    @Getter
    public static class Summary {

        @JsonProperty("defect_count")
        private final int defectCount;

        /** 출장비. 업체가 한 번 방문해 함께 고치므로 하자별 금액에는 없고 총액에 한 번만 들어 있다. */
        @JsonProperty("visit_fee")
        private final int visitFee;

        /** 하자별 금액 합 + 출장비. */
        @JsonProperty("total_cost")
        private final int totalCost;

        @JsonProperty("high_count")
        private final long highCount;

        @JsonProperty("mid_count")
        private final long midCount;

        @JsonProperty("low_count")
        private final long lowCount;

        private Summary(Analysis analysis, List<DefectItemResponse> defects) {
            this.defectCount = defects.size();
            this.totalCost = analysis.getTotalCost() != null ? analysis.getTotalCost() : 0;
            // 저장된 총액에서 하자별 금액을 뺀 값이라, 출장비가 없던 예전 분석도 하자 합 + 출장비 = 총액이 맞는다.
            int defectSum = defects.stream().mapToInt(DefectItemResponse::getEstimatedCost).sum();
            this.visitFee = Math.max(0, this.totalCost - defectSum);
            this.highCount = defects.stream().filter(d -> "HIGH".equalsIgnoreCase(d.getSeverity())).count();
            this.midCount = defects.stream().filter(d -> "MEDIUM".equalsIgnoreCase(d.getSeverity()) || "MID".equalsIgnoreCase(d.getSeverity())).count();
            this.lowCount = defects.stream().filter(d -> "LOW".equalsIgnoreCase(d.getSeverity())).count();
        }

        public static Summary from(Analysis analysis, List<DefectItemResponse> defects) {
            return new Summary(analysis, defects);
        }
    }
}
