-- 26.10.20 11차 DB와 백엔드(JPA 엔티티) 컬럼 불일치 해소용 — 여러 번 실행해도 안전(IF NOT EXISTS)
-- 기존 데이터는 건드리지 않고 "없는 컬럼"만 추가합니다.
ALTER TABLE containers        ADD COLUMN IF NOT EXISTS net_kg              NUMERIC(8,1);
ALTER TABLE containers        ADD COLUMN IF NOT EXISTS assigned_vehicle_no VARCHAR(20);
ALTER TABLE containers        ADD COLUMN IF NOT EXISTS assigned_at         TIMESTAMPTZ;
ALTER TABLE posts             ADD COLUMN IF NOT EXISTS notice_level        VARCHAR(20);
ALTER TABLE tire_management   ADD COLUMN IF NOT EXISTS axle_position       VARCHAR(50);
ALTER TABLE tire_management   ADD COLUMN IF NOT EXISTS disposal_date       TIMESTAMPTZ;
ALTER TABLE tire_management   ADD COLUMN IF NOT EXISTS disposal_mileage    INTEGER;
ALTER TABLE overload_checks   ADD COLUMN IF NOT EXISTS gate_log_id         BIGINT;
ALTER TABLE overload_checks   ADD COLUMN IF NOT EXISTS station_code        VARCHAR(20);
-- 참고: containers.net_kg 는 비어 있으면 적재 가능 중량으로 채움(화면 표시용)
UPDATE containers SET net_kg = max_payload_kg WHERE net_kg IS NULL;
