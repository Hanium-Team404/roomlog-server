package com.roomlog.estimate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roomlog.defect.domain.Defect;
import com.roomlog.global.infra.KakaoLocalClient.KakaoPlace;
import lombok.Getter;

import java.util.List;

@Getter
public class EstimatePreviewResponse {

    @JsonProperty("analysis_id")
    private final Long analysisId;

    @JsonProperty("room_id")
    private final Long roomId;

    private final ProviderInfo provider;
    private final List<DefectSummary> defects;
    private final Summary summary;

    @JsonProperty("message_preview")
    private final String messagePreview;

    private EstimatePreviewResponse(Long analysisId, Long roomId, ProviderInfo provider,
                                    List<DefectSummary> defects, Summary summary, String messagePreview) {
        this.analysisId = analysisId;
        this.roomId = roomId;
        this.provider = provider;
        this.defects = defects;
        this.summary = summary;
        this.messagePreview = messagePreview;
    }

    /** visitFee: 총액에 한 번만 더하는 출장비. 하자별 금액에는 들어 있지 않다. */
    public static EstimatePreviewResponse of(Long analysisId, Long roomId, KakaoPlace place,
                                             List<Defect> defects, int visitFee, String userMessage) {
        ProviderInfo provider = ProviderInfo.from(place);
        List<DefectSummary> defectSummaries = defects.stream().map(DefectSummary::from).toList();
        Summary summary = Summary.of(defectSummaries, visitFee);
        String messagePreview = buildMessagePreview(defects.size(), summary.getTotalCost(), userMessage);
        return new EstimatePreviewResponse(analysisId, roomId, provider, defectSummaries, summary, messagePreview);
    }

    private static String buildMessagePreview(int defectCount, int totalCost, String userMessage) {
        StringBuilder sb = new StringBuilder();
        sb.append("안녕하세요. RoomLog를 통해 문의드립니다.\n");
        sb.append(String.format("현재 총 %d건의 하자가 확인되었으며 예상 수리비는 출장비 포함 약 %,d원입니다.\n", defectCount, totalCost));
        if (userMessage != null && !userMessage.isBlank()) {
            sb.append(userMessage);
        }
        return sb.toString();
    }

    @Getter
    public static class ProviderInfo {

        @JsonProperty("provider_external_id")
        private final String providerExternalId;

        @JsonProperty("provider_name")
        private final String providerName;

        @JsonProperty("provider_phone")
        private final String providerPhone;

        @JsonProperty("provider_address")
        private final String providerAddress;

        private ProviderInfo(KakaoPlace place) {
            this.providerExternalId = place.getId();
            this.providerName = place.getPlaceName();
            this.providerPhone = place.getPhone();
            this.providerAddress = place.getRoadAddressName() != null && !place.getRoadAddressName().isBlank()
                    ? place.getRoadAddressName()
                    : place.getAddressName();
        }

        public static ProviderInfo from(KakaoPlace place) {
            return new ProviderInfo(place);
        }
    }

    @Getter
    public static class DefectSummary {

        @JsonProperty("defect_id")
        private final Long defectId;

        private final String type;
        private final String location;
        private final String severity;

        @JsonProperty("estimated_cost")
        private final Integer estimatedCost;

        private DefectSummary(Defect defect) {
            this.defectId = defect.getId();
            this.type = defect.getType();
            this.location = defect.getLocation();
            this.severity = defect.getSeverity();
            this.estimatedCost = defect.getEstimatedCost();
        }

        public static DefectSummary from(Defect defect) {
            return new DefectSummary(defect);
        }
    }

    @Getter
    public static class Summary {

        @JsonProperty("defect_count")
        private final int defectCount;

        /** 출장비. 업체가 한 번 방문해 함께 고치므로 하자별이 아니라 총액에 한 번만 더한다. */
        @JsonProperty("visit_fee")
        private final int visitFee;

        /** 하자별 금액 합 + 출장비. */
        @JsonProperty("total_cost")
        private final int totalCost;

        private Summary(List<DefectSummary> defects, int visitFee) {
            this.defectCount = defects.size();
            this.visitFee = visitFee;
            this.totalCost = defects.stream().mapToInt(DefectSummary::getEstimatedCost).sum() + visitFee;
        }

        public static Summary of(List<DefectSummary> defects, int visitFee) {
            return new Summary(defects, visitFee);
        }
    }
}
