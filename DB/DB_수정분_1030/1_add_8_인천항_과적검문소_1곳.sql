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
