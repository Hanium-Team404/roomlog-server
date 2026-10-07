-- 특정 방들의 하자 점검 결과만 삭제 (스캔 데이터는 유지)
--
-- 사용법: 아래 @rooms 값(쉼표 구분, 공백 없이)을 바꾼 뒤
--   mysql -h <호스트> -P <포트> -u <유저> -p <DB> < src/main/resources/db/cleanup_room_defects.sql
--   (Railway: railway connect MySQL 후 source src/main/resources/db/cleanup_room_defects.sql)
--
-- 지우는 것 : 해당 방들의 analysis, defect, defect_repair_guide,
--             estimate, estimate_defect, repair, repair_defect, 하자 상담 chat_message
-- 남기는 것 : scan, room, house 등 나머지 전부
-- 주의     : AI 서버 S3의 하자 이미지 파일은 지우지 않는다.

SET @rooms = '135,138,139,142,143';

START TRANSACTION;

DELETE FROM chat_message WHERE defect_id IN (
    SELECT d.defect_id FROM defect d JOIN analysis a ON d.analysis_id = a.analysis_id
    WHERE FIND_IN_SET(a.room_id, @rooms));

DELETE FROM repair_defect WHERE repair_id IN (
    SELECT repair_id FROM repair WHERE FIND_IN_SET(room_id, @rooms));
DELETE FROM repair WHERE FIND_IN_SET(room_id, @rooms);

DELETE FROM estimate_defect WHERE estimate_id IN (
    SELECT estimate_id FROM estimate WHERE FIND_IN_SET(room_id, @rooms));
DELETE FROM estimate WHERE FIND_IN_SET(room_id, @rooms);

DELETE FROM defect_repair_guide WHERE defect_id IN (
    SELECT d.defect_id FROM defect d JOIN analysis a ON d.analysis_id = a.analysis_id
    WHERE FIND_IN_SET(a.room_id, @rooms));

DELETE FROM defect WHERE analysis_id IN (
    SELECT analysis_id FROM analysis WHERE FIND_IN_SET(room_id, @rooms));
DELETE FROM analysis WHERE FIND_IN_SET(room_id, @rooms);

COMMIT;

-- 확인용 (analysis, defect는 0, scan은 그대로여야 한다)
SELECT 'analysis' AS t, COUNT(*) AS cnt FROM analysis WHERE FIND_IN_SET(room_id, @rooms)
UNION ALL SELECT 'defect', COUNT(*) FROM defect d JOIN analysis a ON a.analysis_id = d.analysis_id
    WHERE FIND_IN_SET(a.room_id, @rooms)
UNION ALL SELECT 'scan(유지)', COUNT(*) FROM scan WHERE FIND_IN_SET(room_id, @rooms);
