-- demo_ready.sql (26.11.02): 발표 전 한 번에 실행 - 여러 번 실행해도 같은 결과
-- 1) 과적검문소 1곳만 활성  2) 이상상황 담당자 권한명  3) 알림 · 보고서 도장 시연 초기화

-- [1] 과적검문소
-- add_8 (26.10.28): 과적검문소는 '인천광역시 중구 항동7가 1' 1곳만 활성
--  나머지 weigh_stations 는 is_active=false (삭제하지 않음 — overload_checks FK 유지)
--  적용 전: 아래 1) 확인 쿼리만 실행해 보세요.

-- 1) 확인 (읽기 전용)
-- SELECT station_id, station_code, station_name, address, latitude, longitude, is_active FROM weigh_stations ORDER BY station_id;

-- 2) 적용
BEGIN;
UPDATE weigh_stations
   SET is_active = FALSE
 WHERE station_code IS DISTINCT FROM 'CHECKPOINT-18';

UPDATE weigh_stations
   SET is_active = TRUE,
       station_name = '인천항 과적검문소',
       address = '인천광역시 중구 항동7가 1',
       latitude = 37.4712478,
       longitude = 126.6194982,
       sido = '인천광역시',
       sigungu = '중구',
       alert_radius_m = 2000
 WHERE station_code = 'CHECKPOINT-18';

-- 없으면 삽입 (시드에 없을 때)
INSERT INTO weigh_stations (station_code, station_name, address, tollgate_id, road_name, latitude, longitude, is_active, sido, sigungu, alert_radius_m)
SELECT 'CHECKPOINT-18', '인천항 과적검문소', '인천광역시 중구 항동7가 1', NULL, NULL, 37.4712478, 126.6194982, TRUE, '인천광역시', '중구', 2000
WHERE NOT EXISTS (SELECT 1 FROM weigh_stations WHERE station_code = 'CHECKPOINT-18');
COMMIT;

-- 3) 확인
-- SELECT station_id, station_code, station_name, address, is_active FROM weigh_stations WHERE is_active ORDER BY station_id;


-- [2] 이상상황 담당자 권한명
-- 26.10.29 QA: 시연 데이터의 예외·사고 담당 문구를 실제 권한명으로 교체
UPDATE ops_incidents SET assignee = '통합 관제사'      WHERE assignee = '관제 담당';
UPDATE ops_incidents SET assignee = '곤지암허브 관제사' WHERE assignee = '허브 담당';

-- [3] 시연 초기화: 쌓인 알림 비우기 + 일일보고서 확정 도장 되돌리기
DELETE FROM notifications;
DO $$ BEGIN
  IF to_regclass('report_signs') IS NOT NULL THEN
    DELETE FROM report_signs;
  END IF;
END $$;
-- 26.11.02 백업 관련 알림 정리
DELETE FROM notifications WHERE title LIKE '%백업%' OR message LIKE '%백업%';
