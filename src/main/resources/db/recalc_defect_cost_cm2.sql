-- 기존 하자의 예상 비용을 ㎠ 단가 기준으로 재계산 (코드 변경과 같은 식)
--
-- 하자별 비용 = CEIL((5000 + ㎠단가 × MIN(면적, 2000) + ㎠단가 × 0.5 × MAX(면적 − 2000, 0)) / 100) × 100
--   (㎠단가 = ㎡ 단가 ÷ 100, 면적은 최소 1㎠)
-- 분석 총액   = 하자별 비용 합 + 가장 비싼 하자 종류 출장비(100원 단위 올림)
--
-- 사용법:
--   mysql -h <호스트> -P <포트> -u <유저> -p <DB> < src/main/resources/db/recalc_defect_cost_cm2.sql
-- 새 코드 배포 후 한 번만 실행한다. (여러 번 실행해도 결과는 같다)

START TRANSACTION;

UPDATE defect d
JOIN repair_unit_price p ON p.defect_type = d.type AND p.severity = d.severity
SET d.estimated_cost = CEIL((
        5000
        + (p.unit_price / 100) * LEAST(GREATEST(IFNULL(d.area, 0), 1), 2000)
        + (p.unit_price / 100) * 0.5 * GREATEST(GREATEST(IFNULL(d.area, 0), 1) - 2000, 0)
    ) / 100) * 100;

UPDATE analysis a
SET a.total_cost = (
        SELECT IFNULL(SUM(d.estimated_cost), 0)
        FROM defect d WHERE d.analysis_id = a.analysis_id AND d.is_deleted = 0
    ) + IFNULL((
        SELECT CEIL(MAX(f.fee) / 100) * 100
        FROM defect d JOIN repair_visit_fee f ON f.defect_type = d.type
        WHERE d.analysis_id = a.analysis_id AND d.is_deleted = 0
    ), 0)
WHERE a.status = 'COMPLETED';

COMMIT;

-- 확인용
SELECT d.defect_id, d.type, d.severity, ROUND(d.area) area_cm2, d.estimated_cost
FROM defect d WHERE d.is_deleted = 0 ORDER BY d.defect_id DESC LIMIT 20;
