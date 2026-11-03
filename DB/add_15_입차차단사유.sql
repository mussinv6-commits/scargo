-- add_15 (26.11.02): 허브 입차 차단 사유 컬럼 - 여러 번 실행해도 안전
-- 입차 판정 변경(진입 허가 기준)으로 HubVehicleLog.blockReason 이 생겼는데, DB(ddl-auto: none)에는 컬럼이 없어 허브 입차 조회 · 기록이 오류 남
ALTER TABLE hub_vehicle_logs ADD COLUMN IF NOT EXISTS block_reason varchar(12);
COMMENT ON COLUMN hub_vehicle_logs.block_reason IS 'BLOCK 사유: UNREADABLE 판독 불가 / UNREGISTERED 미등록 / PENDING 심사대기 / REJECTED 불허';
