package com.roomlog.defect.service;

import com.roomlog.defect.domain.Defect;
import com.roomlog.defect.domain.RepairUnitPrice;
import com.roomlog.defect.domain.RepairVisitFee;
import com.roomlog.defect.repository.RepairUnitPriceRepository;
import com.roomlog.defect.repository.RepairVisitFeeRepository;
import com.roomlog.global.exception.CustomException;
import com.roomlog.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 수리 예상 비용 계산.
 * 하자별 금액 = ㎡당 단가(종류, 심각도) × 면적. 100원 단위로 올린다.
 * 총액 = 하자별 금액 합 + 포함된 하자 종류 중 가장 비싼 출장비 한 번.
 */
@Component
@RequiredArgsConstructor
public class RepairCostCalculator {

    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH");
    private static final int ROUNDING_UNIT = 100;

    private final RepairUnitPriceRepository repairUnitPriceRepository;
    private final RepairVisitFeeRepository repairVisitFeeRepository;

    public int defectCost(String type, String severity, Float area) {
        String normalizedSeverity = severity == null ? "" : severity.toUpperCase();
        if (!SEVERITIES.contains(normalizedSeverity)) {
            throw new CustomException(ErrorCode.COMMON_400, "유효하지 않은 severity 값: " + severity);
        }

        RepairUnitPrice unitPrice = repairUnitPriceRepository.findByDefectTypeAndSeverity(type, normalizedSeverity)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMON_400, "단가가 없는 하자 종류: " + type));

        double cost = unitPrice.getUnitPrice() * (area != null ? area : 0f);
        return (int) (Math.ceil(cost / ROUNDING_UNIT) * ROUNDING_UNIT);
    }

    /** 하자 종류 중 가장 비싼 출장비. 하자가 없으면 0. */
    public int visitFee(Collection<String> defectTypes) {
        if (defectTypes.isEmpty()) return 0;
        return repairVisitFeeRepository.findAllById(Set.copyOf(defectTypes)).stream()
                .mapToInt(RepairVisitFee::getFee)
                .max()
                .orElse(0);
    }

    public int totalCost(List<Defect> defects) {
        int defectSum = defects.stream().mapToInt(Defect::getEstimatedCost).sum();
        return defectSum + visitFee(defects.stream().map(Defect::getType).toList());
    }
}
