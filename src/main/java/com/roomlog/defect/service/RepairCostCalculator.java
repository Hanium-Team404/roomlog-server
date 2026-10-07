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
 * 하자별 금액 = 기본료 + ㎠당 단가(종류, 심각도) × 하자 면적(㎠). 하자별 금액과 출장비는 100원 단위로 올린다.
 * 기본료는 하자 하나를 손보는 최소 작업비(정책값)로, 8㎠ 흠집이 수백 원으로 나오지 않게 한다.
 * ㎠당 단가는 ㎡당 표준시장단가의 1/100로 정한다(정책값). 산술 환산(1/10,000)을 쓰면 면적분이 거의 0이고,
 * 시공 단위(벽지 1폭)로 올림하면 면적이 달라도 금액이 전부 같아져 둘 다 쓰지 않는다.
 * 총액 = 하자별 금액 합 + 포함된 하자 종류 중 가장 비싼 출장비 한 번.
 */
@Component
@RequiredArgsConstructor
public class RepairCostCalculator {

    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH");
    private static final int ROUNDING_UNIT = 100;
    /** 하자 1건당 기본 작업비(원). */
    private static final int BASE_FEE = 5_000;
    /** ㎠당 단가 = ㎡당 단가 ÷ 이 값. */
    private static final double M2_PRICE_TO_CM2_PRICE = 100d;
    /** 면적이 0으로 오더라도 하자는 있으므로 최소 이 면적으로 계산한다. */
    private static final double MIN_AREA_CM2 = 1d;

    private final RepairUnitPriceRepository repairUnitPriceRepository;
    private final RepairVisitFeeRepository repairVisitFeeRepository;

    /** @param areaCm2 하자 면적(㎠). AI와 앱은 ㎠를 쓰고, 단가 테이블은 ㎡ 기준이라 여기서만 ㎠ 단가로 바꾼다. */
    public int defectCost(String type, String severity, Float areaCm2) {
        String normalizedSeverity = severity == null ? "" : severity.toUpperCase();
        if (!SEVERITIES.contains(normalizedSeverity)) {
            throw new CustomException(ErrorCode.COMMON_400, "유효하지 않은 severity 값: " + severity);
        }

        RepairUnitPrice unitPrice = repairUnitPriceRepository.findByDefectTypeAndSeverity(type, normalizedSeverity)
                .orElseThrow(() -> new CustomException(ErrorCode.COMMON_400, "단가가 없는 하자 종류: " + type));

        double area = Math.max(MIN_AREA_CM2, areaCm2 != null ? areaCm2 : 0d);
        double pricePerCm2 = unitPrice.getUnitPrice() / M2_PRICE_TO_CM2_PRICE;
        return roundUp(BASE_FEE + pricePerCm2 * area);
    }

    /** 하자 종류 중 가장 비싼 출장비. 하자별 금액과 같이 100원 단위로 올린다. 하자가 없으면 0. */
    public int visitFee(Collection<String> defectTypes) {
        if (defectTypes.isEmpty()) return 0;
        int fee = repairVisitFeeRepository.findAllById(Set.copyOf(defectTypes)).stream()
                .mapToInt(RepairVisitFee::getFee)
                .max()
                .orElse(0);
        return roundUp(fee);
    }

    private int roundUp(double amount) {
        return (int) (Math.ceil(amount / ROUNDING_UNIT) * ROUNDING_UNIT);
    }

    public int totalCost(List<Defect> defects) {
        int defectSum = defects.stream().mapToInt(Defect::getEstimatedCost).sum();
        return defectSum + visitFee(defects.stream().map(Defect::getType).toList());
    }
}
