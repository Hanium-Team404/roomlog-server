-- 특정 방의 하자 점검 결과만 삭제 (스캔 데이터는 유지)
--
-- 사용법: 아래 @room 값을 바꾼 뒤
--   mysql -h <호스트> -P <포트> -u <유저> -p <DB> < src/main/resources/db/cleanup_room_defects.sql
--
-- 지우는 것 : 해당 방의 analysis, defect, defect_repair_guide,
--             estimate, estimate_defect, repair, repair_defect, 하자 상담 chat_message
-- 남기는 것 : scan, room, house 등 나머지 전부

SET @room = 135;

START TRANSACTION;

DELETE FROM chat_message WHERE defect_id IN (
    SELECT d.defect_id FROM defect d JOIN analysis a ON d.analysis_id = a.analysis_id WHERE a.room_id = @room);

DELETE FROM repair_defect WHERE repair_id IN (SELECT repair_id FROM repair WHERE room_id = @room);
DELETE FROM repair WHERE room_id = @room;

DELETE FROM estimate_defect WHERE estimate_id IN (SELECT estimate_id FROM estimate WHERE room_id = @room);
DELETE FROM estimate WHERE room_id = @room;

DELETE FROM defect_repair_guide WHERE defect_id IN (
    SELECT d.defect_id FROM defect d JOIN analysis a ON d.analysis_id = a.analysis_id WHERE a.room_id = @room);

DELETE FROM defect WHERE analysis_id IN (SELECT analysis_id FROM analysis WHERE room_id = @room);
DELETE FROM analysis WHERE room_id = @room;

COMMIT;

-- 확인용 (둘 다 0이어야 한다)
SELECT 'analysis' AS t, COUNT(*) AS cnt FROM analysis WHERE room_id = @room
UNION ALL SELECT 'defect', COUNT(*) FROM defect d JOIN analysis a ON a.analysis_id = d.analysis_id WHERE a.room_id = @room;
