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
 * 하자별 금액 = ㎡당 단가(종류, 심각도) × 시공 면적. 100원 단위로 올린다.
 * 시공 면적 = 하자 면적을 시공 최소 단위(벽지 1폭, 보드 1장 등)로 올림한 값. 3㎠ 흠집도 벽지 1폭은 갈아야 하므로
 * 하자 면적에 단가를 그대로 곱하면 100원 같은 비현실적인 금액이 나온다. 하자 면적 자체는 바꾸지 않는다.
 * 총액 = 하자별 금액 합 + 포함된 하자 종류 중 가장 비싼 출장비 한 번.
 */
@Component
@RequiredArgsConstructor
public class RepairCostCalculator {

    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH");
    private static final int ROUNDING_UNIT = 100;
    private static final double CM2_PER_M2 = 10_000d;

    private final RepairUnitPriceRepository repairUnitPriceRepository;
    private final RepairVisitFeeRepository repairVisitFeeRepository;

    /** @param areaCm2 하자 면적(㎠). AI와 앱은 ㎠를 쓰고, 단가는 ㎡ 기준이라 여기서만 환산한다. */
    public int defectCost(String type, String severity, Float areaCm2) {
        String normalizedSeverity = severity == null ? "" : severity.toUpperCase();
        if (!SEVERITIES.contains(normalizedSeverity)) {
            throw new CustomException(ErrorCode.COMMON_400, "유효하지 않은 severity 값: " + severity);
        }

        RepairUnitPrice unitPrice = repairUnitPriceRepository.findByDefectTypeAndSeverity(type, normalizedSeverity)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMON_400, "단가가 없는 하자 종류: " + type));

        double areaM2 = (areaCm2 != null ? areaCm2 : 0d) / CM2_PER_M2;
        double cost = unitPrice.getUnitPrice() * billedArea(areaM2, unitPrice.getWorkUnitArea());
        return (int) (Math.ceil(cost / ROUNDING_UNIT) * ROUNDING_UNIT);
    }

    /** 하자 면적(㎡)을 시공 단위(㎡)로 올림. 면적이 0이어도 하자는 있으므로 최소 1단위. 단위가 없으면 하자 면적 그대로. */
    private double billedArea(double defectArea, Float workUnitArea) {
        if (workUnitArea == null || workUnitArea <= 0) return defectArea;
        double units = Math.max(1, Math.ceil(defectArea / workUnitArea));
        return units * workUnitArea;
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
