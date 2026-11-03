-- 26.11.02 허브 게이트 입차: 진입 허가(trucks.entry_approval) 확인 + 차단 사유 기록
--  실행: psql -U postgres -d scargo -f add_14_게이트_진입허가.sql   (다시 실행해도 안전)
--  block_reason (decision = 'BLOCK' 일 때만 값이 있음)
--    UNREADABLE   번호판 판독 불가
--    UNREGISTERED S카고 차량(trucks) 미등록
--    PENDING      진입 허가 심사대기
--    REJECTED     진입 불허
--  기존 기록은 NULL 로 둔다 (예전 BLOCK = hub_vehicles 미등록 기준이라 사유를 거꾸로 채우지 않음)
ALTER TABLE hub_vehicle_logs ADD COLUMN IF NOT EXISTS block_reason VARCHAR(12);
ALTER TABLE hub_vehicle_logs DROP CONSTRAINT IF EXISTS chk_hub_vehicle_logs_block_reason;
ALTER TABLE hub_vehicle_logs ADD CONSTRAINT chk_hub_vehicle_logs_block_reason
    CHECK (block_reason IS NULL OR block_reason IN ('UNREADABLE', 'UNREGISTERED', 'PENDING', 'REJECTED'));
COMMENT ON COLUMN hub_vehicle_logs.block_reason IS '입차 차단 사유: UNREADABLE / UNREGISTERED / PENDING(진입 허가 심사대기) / REJECTED(진입 불허)';
