package com.roomlog.global.config;

import com.roomlog.defect.domain.RepairUnitPrice;
import com.roomlog.defect.domain.RepairVisitFee;
import com.roomlog.defect.repository.RepairUnitPriceRepository;
import com.roomlog.defect.repository.RepairVisitFeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 수리 비용 기본 데이터.
 *
 * [㎡당 단가] 2026년 하반기 건설공사 표준시장단가(국토교통부) 공종 단가 + 주재료비(시중 판매가 ㎡당 환산).
 *  D  도배      = 도배지바름 석고보드면/벽 OB110.10000 6,588 + 실크벽지 1,800(평당 6,000원)        = 8,388
 *  J  줄퍼티    = 석고보드면 바탕만들기 줄퍼티/벽 NS062.01010 10,398 + 퍼티 1,200(25kg 25,000원, ㎡당 1.2kg) = 11,598
 *  O  올퍼티    = 석고보드면 바탕만들기 올퍼티/벽 NS062.01000 19,238 + 퍼티 1,200                    = 20,438
 *  M  모르타르  = 시멘트모르타르 바름 1회 3.6m 이하 GA110.01100 14,589 (재료비 근거 없음, 미포함)       = 14,589
 *  G1 석고판1겹 = 석고판 못붙임 바탕용 1겹/벽 OC311.00010 9,928 + 석고보드 2,200(1.62㎡ 3,580원)      = 12,128
 *  G2 석고판2겹 = 석고판 못붙임 바탕용 2겹/벽 OC311.00020 13,927 + 석고보드 4,400                    = 18,327
 *  P1 페인트1회 = 수성페인트 롤러칠 1회/벽 NC102.20000 3,380 + 페인트 2,750(1L 16,500원, 1회 약 6㎡)  = 6,130
 *
 * [계산] 하자별 비용 = 기본료 5,000원 + (㎡당 단가 ÷ 100)원/㎠ × 하자 면적(㎠). 2,000㎠ 초과분은 단가 절반.
 *        자세한 이유는 RepairCostCalculator 참고.
 *
 * [출장비] 2026.09.01 적용 건설업 시중노임단가(대한건설협회) 해당 직종 일당의 0.5일분.
 *
 * 단가표가 개정되면(표준시장단가 1월·5월, 노임단가 1월·9월) 테이블 값만 수정하면 된다.
 */
@Component
@RequiredArgsConstructor
public class RepairCostInitializer implements ApplicationRunner {

    private static final String STANDARD_PRICE = "2026 하반기 표준시장단가";

    private final RepairUnitPriceRepository repairUnitPriceRepository;
    private final RepairVisitFeeRepository repairVisitFeeRepository;

    @Override
    public void run(ApplicationArguments args) {
        seedUnitPrices();
        seedVisitFees();
    }

    private void seedUnitPrices() {
        if (repairUnitPriceRepository.count() > 0) return;

        String d = "도배(OB110.10000 6,588 + 벽지 1,800)";
        String j = "줄퍼티(NS062.01010 10,398 + 퍼티 1,200)";
        String o = "올퍼티(NS062.01000 19,238 + 퍼티 1,200)";
        String m = "모르타르 바름(GA110.01100 14,589)";
        String g1 = "석고판 1겹(OC311.00010 9,928 + 석고보드 2,200)";
        String g2 = "석고판 2겹(OC311.00020 13,927 + 석고보드 4,400)";
        String p1 = "수성페인트 1회(NC102.20000 3,380 + 페인트 2,750)";

        repairUnitPriceRepository.saveAll(List.of(
                price("SCRATCH", "LOW", 8_388, d),
                price("SCRATCH", "MEDIUM", 19_986, j, d),
                price("SCRATCH", "HIGH", 28_826, o, d),

                price("PEELING", "LOW", 8_388, d),
                price("PEELING", "MEDIUM", 19_986, j, d),
                price("PEELING", "HIGH", 28_826, o, d),

                price("CRACK", "LOW", 19_986, j, d),
                price("CRACK", "MEDIUM", 28_826, o, d),
                price("CRACK", "HIGH", 43_415, m, o, d),

                price("BREAKAGE", "LOW", 28_826, o, d),
                price("BREAKAGE", "MEDIUM", 40_954, g1, o, d),
                price("BREAKAGE", "HIGH", 47_153, g2, o, d),

                // 오염은 페인트 덧칠 → 재도배 → 바탕 정리 후 재도배 순으로 공정이 늘어난다.
                price("STAIN", "LOW", 6_130, p1),
                price("STAIN", "MEDIUM", 8_388, d),
                price("STAIN", "HIGH", 19_986, j, d)
        ));
    }

    private void seedVisitFees() {
        if (repairVisitFeeRepository.count() > 0) return;

        repairVisitFeeRepository.saveAll(List.of(
                fee("SCRATCH", "도배공", 229_833),
                fee("PEELING", "도배공", 229_833),
                fee("CRACK", "도배공", 229_833),
                fee("STAIN", "도장공", 268_225),
                fee("BREAKAGE", "내장공", 258_904)
        ));
    }

    private RepairUnitPrice price(String defectType, String severity, int unitPrice, String... processes) {
        return RepairUnitPrice.builder()
                .defectType(defectType)
                .severity(severity)
                .unitPrice(unitPrice)
                .basis(STANDARD_PRICE + ": " + String.join(" + ", processes))
                .build();
    }

    private RepairVisitFee fee(String defectType, String trade, int dailyWage) {
        return RepairVisitFee.builder()
                .defectType(defectType)
                .trade(trade)
                .fee((int) Math.round(dailyWage * 0.5))
                .basis("2026.09.01 적용 건설업 시중노임단가(대한건설협회) " + trade + " 일당 "
                        + String.format("%,d", dailyWage) + "원 × 0.5일")
                .build();
    }
}
