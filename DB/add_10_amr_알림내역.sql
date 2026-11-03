-- 26.11.01 AMR 알림 내역 (S-Bot 「AMR 알림 내역」)
-- 허브 화재(hub) · 배터리 화재(batt) · 온도 이상(bfire stage1) · 비상 정지(estop)
-- 기존 DB에 안전하게 추가 (IF NOT EXISTS). 백엔드 기동 시에도 동일 DDL을 한 번 더 보장한다.

CREATE TABLE IF NOT EXISTS amr_safety_alerts (
  alert_id      BIGSERIAL PRIMARY KEY,
  event_type    VARCHAR(20)  NOT NULL,
  title         VARCHAR(200) NOT NULL,
  message       TEXT,
  equipment     VARCHAR(120),
  zone_label    VARCHAR(120),
  kind          VARCHAR(20),
  code          VARCHAR(60),
  started_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  ended_at      TIMESTAMPTZ,
  duration_sec  INTEGER,
  active        BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  CONSTRAINT chk_amr_safety_event_type CHECK (event_type IN ('FIRE','BATT','ESTOP','TEMP'))
);

CREATE INDEX IF NOT EXISTS idx_amr_safety_alerts_started ON amr_safety_alerts (started_at DESC);
CREATE INDEX IF NOT EXISTS idx_amr_safety_alerts_type ON amr_safety_alerts (event_type, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_amr_safety_alerts_active ON amr_safety_alerts (active) WHERE active;

CREATE TABLE IF NOT EXISTS amr_safety_alert_reads (
  alert_id   BIGINT NOT NULL REFERENCES amr_safety_alerts(alert_id) ON DELETE CASCADE,
  account_id BIGINT NOT NULL REFERENCES accounts(account_id) ON DELETE CASCADE,
  read_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (alert_id, account_id)
);

CREATE INDEX IF NOT EXISTS idx_amr_safety_alert_reads_account ON amr_safety_alert_reads (account_id);

COMMENT ON TABLE amr_safety_alerts IS '26.11.01 S-Bot AMR 알림 내역 (화재·배터리·온도이상·비상정지)';
COMMENT ON TABLE amr_safety_alert_reads IS '계정별 읽음 상태';