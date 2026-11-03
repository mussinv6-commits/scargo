-- add_11_기사앱_추가메뉴.sql
-- 기사앱 추가 메뉴용 테이블 (IF NOT EXISTS). 기존 exceptions / vehicle_checklist / container_inspections 활용.

CREATE TABLE IF NOT EXISTS driver_incidents (
  incident_id        BIGSERIAL PRIMARY KEY,
  exception_id       BIGINT REFERENCES exceptions(exception_id),
  account_id         BIGINT NOT NULL REFERENCES accounts(account_id),
  category           VARCHAR(30) NOT NULL,
  severity           VARCHAR(10) NOT NULL,
  assignment_id      BIGINT,
  order_id           BIGINT,
  lat                DOUBLE PRECISION,
  lng                DOUBLE PRECISION,
  location_text      VARCHAR(200),
  message            TEXT NOT NULL,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_driver_incidents_category CHECK (category IN ('ACCIDENT','BREAKDOWN','CARGO_DAMAGE','OFF_ROUTE','OTHER')),
  CONSTRAINT chk_driver_incidents_severity CHECK (severity IN ('CRITICAL','WARNING'))
);
CREATE INDEX IF NOT EXISTS idx_driver_incidents_account ON driver_incidents(account_id, created_at DESC);

CREATE TABLE IF NOT EXISTS driver_incident_photos (
  photo_id     BIGSERIAL PRIMARY KEY,
  incident_id  BIGINT NOT NULL REFERENCES driver_incidents(incident_id) ON DELETE CASCADE,
  file_path    VARCHAR(500) NOT NULL,
  content_type VARCHAR(100),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS driver_documents (
  document_id  BIGSERIAL PRIMARY KEY,
  account_id   BIGINT NOT NULL REFERENCES accounts(account_id),
  doc_type     VARCHAR(40) NOT NULL,
  title        VARCHAR(100) NOT NULL,
  file_path    VARCHAR(500),
  expires_at   DATE,
  notes        VARCHAR(200),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_driver_documents_type CHECK (doc_type IN ('LICENSE','FREIGHT_QUAL','VEHICLE_REG','CARGO_INSURANCE','OTHER'))
);
CREATE INDEX IF NOT EXISTS idx_driver_documents_account ON driver_documents(account_id, expires_at);

CREATE TABLE IF NOT EXISTS driver_inquiries (
  inquiry_id   BIGSERIAL PRIMARY KEY,
  account_id   BIGINT NOT NULL REFERENCES accounts(account_id),
  category     VARCHAR(40) NOT NULL,
  subject      VARCHAR(100) NOT NULL,
  body         TEXT NOT NULL,
  status       VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_driver_inquiries_status CHECK (status IN ('OPEN','ANSWERED','CLOSED'))
);
CREATE INDEX IF NOT EXISTS idx_driver_inquiries_account ON driver_inquiries(account_id, created_at DESC);

CREATE TABLE IF NOT EXISTS driver_app_settings (
  account_id         BIGINT PRIMARY KEY REFERENCES accounts(account_id),
  tts_enabled        BOOLEAN NOT NULL DEFAULT TRUE,
  tts_volume         NUMERIC(3,2) NOT NULL DEFAULT 1.00,
  tts_rate           NUMERIC(3,2) NOT NULL DEFAULT 1.00,
  alert_dispatch     BOOLEAN NOT NULL DEFAULT TRUE,
  alert_exception    BOOLEAN NOT NULL DEFAULT TRUE,
  alert_fire         BOOLEAN NOT NULL DEFAULT TRUE,
  alert_notice       BOOLEAN NOT NULL DEFAULT TRUE,
  checkpoint_allow   TEXT NOT NULL DEFAULT 'CHECKPOINT-18,인천항 과적검문소',
  large_text         BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
