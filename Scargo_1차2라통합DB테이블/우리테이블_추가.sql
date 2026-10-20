-- 26.10.20 11차 DB 위에 "우리 허브 관제 전용" 테이블만 추가 (팀 테이블은 건드리지 않음). 여러 번 실행해도 안전.
ALTER TABLE amr_maintenance ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ;
ALTER TABLE amr_maintenance ADD COLUMN IF NOT EXISTS approved_by VARCHAR(50);

CREATE TABLE IF NOT EXISTS amr_settings (
  hub_id BIGINT PRIMARY KEY,
  rul_threshold_hours INTEGER NOT NULL DEFAULT 24,
  charge_at_pct INTEGER NOT NULL DEFAULT 30,
  updated_by VARCHAR(50),
  updated_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS amr_otp_keys (
  key_id BIGSERIAL PRIMARY KEY,
  label VARCHAR(40) NOT NULL,
  secret VARCHAR(64) NOT NULL,
  active BOOLEAN NOT NULL,
  created_by VARCHAR(50),
  created_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS amr_auth_tokens (
  token_hash VARCHAR(64) PRIMARY KEY,
  client VARCHAR(10) NOT NULL,
  key_id BIGINT NOT NULL,
  ip VARCHAR(60),
  created_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  last_used_at TIMESTAMPTZ,
  revoked BOOLEAN NOT NULL
);
CREATE TABLE IF NOT EXISTS amr_task_logs (
  task_id BIGSERIAL PRIMARY KEY,
  hub_id BIGINT NOT NULL,
  robot_code VARCHAR(20) NOT NULL,
  task_type VARCHAR(10) NOT NULL,
  waybill_no VARCHAR(30),
  barcode VARCHAR(13),
  dest_hub VARCHAR(20),
  handle_type VARCHAR(20),
  weight_kg DOUBLE PRECISION,
  from_loc VARCHAR(30),
  to_loc VARCHAR(30),
  duration_sec DOUBLE PRECISION,
  inter_wait_sec DOUBLE PRECISION,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS hub_vehicles (
  plate_no VARCHAR(20) PRIMARY KEY,
  vehicle_kind VARCHAR(10) NOT NULL,
  carrier VARCHAR(30),
  memo VARCHAR(100),
  created_at TIMESTAMPTZ
);
CREATE TABLE IF NOT EXISTS hub_vehicle_logs (
  log_id BIGSERIAL PRIMARY KEY,
  hub_id BIGINT NOT NULL,
  direction VARCHAR(10) NOT NULL,
  recognized_plate VARCHAR(20),
  photo_plate VARCHAR(20),
  vehicle_kind VARCHAR(10),
  decision VARCHAR(10) NOT NULL,
  lane VARCHAR(5),
  dock_no INTEGER,
  ocr_confidence DOUBLE PRECISION,
  ocr_engine VARCHAR(20),
  image_url VARCHAR(300),
  box_count INTEGER,
  barcodes TEXT,
  dest_hub VARCHAR(20),
  created_at TIMESTAMPTZ,
  seal_status VARCHAR(10),
  seal_input VARCHAR(30)
);
