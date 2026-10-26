-- =====================================================================
-- 06_share통합_추가_20261026.sql
-- scargo_1(2차 DB, 00_scargo_db_최종통합.sql) + SCargo_share(26.10.24~26 팀 공유본) 코드 통합용 추가 스키마
--
-- 실행 순서
--   새 설치 : 00_scargo_db_최종통합.sql → 이 파일
--   기존 DB : (2차 마이그레이션 02~05 까지 적용된 DB) → 이 파일
--   여러 번 실행해도 안전 (IF NOT EXISTS / DROP ... IF EXISTS)
--
-- 내용
--   1. accounts.chk_user_type: 회원가입 화면용 GENERAL · CORPORATE_PENDING 추가 (share)
--   2. amr_otp_keys.hub_id: 허브별 앱 연결 코드 (share 26.10.22)
--   3. amr_auth_token_audits + amr_auth_tokens.audit_id: AMR 출입증 감사 보존 (share 26.10.23)
--   4. container_inspections · container_inspection_audits · container_inspection_photos: 컨테이너 점검표 (share 26.10.23~24)
--      ※ 2차 기사 앱(POST /api/app/inspections/container)이 넣는 한 번에 끝나는 점검은 record_status 기본값 FINALIZED 로 들어감
--   5. ops_incidents: 예외 · 사고 접수 (share 26.10.24)
--   6. gate_reservations: 게이트 사전 예약 (share 26.10.24)
--   7. shipment_legs.received_by_account_id: 지역센터 인수 확정자 (share 26.10.23)
--
-- ※ share 압축에 없던 원본 SQL(SCARGO_Integrated_Full_Install_20261024.sql, add_1~4)을 대신해
--   통합 코드(엔티티 · JdbcTemplate SQL)가 쓰는 컬럼 · 제약 이름에 맞춰 작성했다. 원본을 구하면 DB팀이 비교 검토할 것
-- =====================================================================

BEGIN;

-- 1. 권한 9종 ---------------------------------------------------------
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS chk_user_type;
ALTER TABLE accounts ADD CONSTRAINT chk_user_type CHECK (user_type IN
    ('ADMIN', 'CONTROL', 'HUB_OP', 'CENTER_OP', 'CONTAINER_DRIVER', 'BRANCH_DRIVER', 'CORPORATE_APPROVED',
     'GENERAL', 'CORPORATE_PENDING'));
COMMENT ON CONSTRAINT chk_user_type ON accounts IS
    '권한 9종: 2차 7종 + 회원가입 화면의 일반회원(GENERAL) · 기업회원 승인 대기(CORPORATE_PENDING)';

-- 2. 허브별 앱 연결 코드 -----------------------------------------------
ALTER TABLE amr_otp_keys ADD COLUMN IF NOT EXISTS hub_id BIGINT;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_amr_otp_keys_hub') THEN
    ALTER TABLE amr_otp_keys ADD CONSTRAINT fk_amr_otp_keys_hub FOREIGN KEY (hub_id) REFERENCES hubs(hub_id) ON DELETE CASCADE;
  END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_amr_otp_keys_hub ON amr_otp_keys (hub_id) WHERE active;
COMMENT ON COLUMN amr_otp_keys.hub_id IS 'NULL = 관리자(ADMIN)가 만든 전체 허브용 키, 값 = 허브 관제사(HUB_OP)의 소속 허브 키';

-- 3. AMR 출입증 감사 보존 ---------------------------------------------
CREATE TABLE IF NOT EXISTS amr_auth_token_audits (
    audit_id             BIGSERIAL PRIMARY KEY,
    key_id               BIGINT,
    hub_id               BIGINT,                 -- 발급 키의 허브 (NULL = 전체 허브 키)
    client               VARCHAR(10),
    ip                   VARCHAR(60),
    user_agent           VARCHAR(300),
    issued_by_account_id BIGINT,
    issued_by_user_id    VARCHAR(50),
    issued_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at           TIMESTAMPTZ,
    revoked              BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at           TIMESTAMPTZ,
    revoked_by           VARCHAR(50),
    revoke_reason        VARCHAR(30)
);
CREATE INDEX IF NOT EXISTS idx_amr_token_audits_issued ON amr_auth_token_audits (issued_at DESC);
CREATE INDEX IF NOT EXISTS idx_amr_token_audits_hub ON amr_auth_token_audits (hub_id, issued_at DESC);
COMMENT ON TABLE amr_auth_token_audits IS 'AMR 앱 출입증 발급 · 폐기 감사 (토큰 해시는 저장하지 않음). 만료 출입증 행이 지워져도 남는다';

ALTER TABLE amr_auth_tokens ADD COLUMN IF NOT EXISTS audit_id BIGINT;

CREATE OR REPLACE FUNCTION scargo_amr_token_audit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_hub BIGINT; v_id BIGINT;
BEGIN
  IF TG_OP = 'INSERT' THEN
    SELECT hub_id INTO v_hub FROM amr_otp_keys WHERE key_id = NEW.key_id;
    INSERT INTO amr_auth_token_audits (key_id, hub_id, client, ip, user_agent, issued_by_account_id, issued_by_user_id,
                                       issued_at, expires_at, revoked, revoked_at, revoked_by, revoke_reason)
    VALUES (NEW.key_id, v_hub, NEW.client, NEW.ip, NEW.user_agent, NEW.issued_by_account_id, NEW.issued_by_user_id,
            coalesce(NEW.created_at, CURRENT_TIMESTAMP), NEW.expires_at, coalesce(NEW.revoked, FALSE), NEW.revoked_at, NEW.revoked_by, NEW.revoke_reason)
    RETURNING audit_id INTO v_id;
    NEW.audit_id := v_id;
  ELSIF NEW.audit_id IS NOT NULL AND (NEW.revoked IS DISTINCT FROM OLD.revoked OR NEW.revoked_at IS DISTINCT FROM OLD.revoked_at
        OR NEW.revoked_by IS DISTINCT FROM OLD.revoked_by OR NEW.revoke_reason IS DISTINCT FROM OLD.revoke_reason) THEN
    UPDATE amr_auth_token_audits
       SET revoked = NEW.revoked, revoked_at = coalesce(NEW.revoked_at, revoked_at),
           revoked_by = coalesce(NEW.revoked_by, revoked_by), revoke_reason = coalesce(NEW.revoke_reason, revoke_reason)
     WHERE audit_id = NEW.audit_id;
  END IF;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_amr_token_audit ON amr_auth_tokens;
CREATE TRIGGER trg_amr_token_audit BEFORE INSERT OR UPDATE ON amr_auth_tokens
    FOR EACH ROW EXECUTE FUNCTION scargo_amr_token_audit();

-- 4. 컨테이너 점검표 --------------------------------------------------
--  (2차 16_컨테이너점검표 의 단순 표를 확장. 16 파일로 이미 만든 DB 는 아래 ALTER 로 맞춘다)
CREATE TABLE IF NOT EXISTS container_inspections (
    inspection_id      BIGSERIAL PRIMARY KEY,
    order_id           BIGINT REFERENCES transport_orders(order_id) ON DELETE SET NULL,
    driver_account_id  BIGINT REFERENCES accounts(account_id) ON DELETE SET NULL,
    container_no       VARCHAR(11),
    items              JSONB NOT NULL DEFAULT '{}'::jsonb,
    overall_status     VARCHAR(10) NOT NULL DEFAULT 'PASS',
    memo               TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE container_inspections ADD COLUMN IF NOT EXISTS company_id              BIGINT REFERENCES companies(company_id) ON DELETE SET NULL;
ALTER TABLE container_inspections ADD COLUMN IF NOT EXISTS hub_id                  BIGINT REFERENCES hubs(hub_id) ON DELETE SET NULL;
ALTER TABLE container_inspections ADD COLUMN IF NOT EXISTS created_by_account_id   BIGINT REFERENCES accounts(account_id) ON DELETE SET NULL;
ALTER TABLE container_inspections ADD COLUMN IF NOT EXISTS finalized_by_account_id BIGINT REFERENCES accounts(account_id) ON DELETE SET NULL;
ALTER TABLE container_inspections ADD COLUMN IF NOT EXISTS cancelled_by_account_id BIGINT REFERENCES accounts(account_id) ON DELETE SET NULL;
ALTER TABLE container_inspections ADD COLUMN IF NOT EXISTS record_status           VARCHAR(20) NOT NULL DEFAULT 'FINALIZED';
ALTER TABLE container_inspections ADD COLUMN IF NOT EXISTS finalized_at            TIMESTAMPTZ;
ALTER TABLE container_inspections ADD COLUMN IF NOT EXISTS cancelled_at            TIMESTAMPTZ;
ALTER TABLE container_inspections ADD COLUMN IF NOT EXISTS cancel_reason           TEXT;
ALTER TABLE container_inspections ADD COLUMN IF NOT EXISTS previous_inspection_id  BIGINT REFERENCES container_inspections(inspection_id) ON DELETE RESTRICT;
ALTER TABLE container_inspections ADD COLUMN IF NOT EXISTS updated_at              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- 판정: 7개가 다 들어오기 전 PENDING (웹 · 앱 작성 중), 모두 양호 PASS, 하나라도 불량 FAIL
DO $$ DECLARE r RECORD; BEGIN
  FOR r IN SELECT conname FROM pg_constraint WHERE conrelid = 'container_inspections'::regclass AND contype = 'c'
            AND pg_get_constraintdef(oid) LIKE '%overall_status%' LOOP
    EXECUTE format('ALTER TABLE container_inspections DROP CONSTRAINT %I', r.conname);
  END LOOP;
END $$;
ALTER TABLE container_inspections ADD CONSTRAINT chk_ci_overall_status CHECK (overall_status IN ('PENDING', 'PASS', 'FAIL'));
ALTER TABLE container_inspections DROP CONSTRAINT IF EXISTS chk_ci_record_status;
ALTER TABLE container_inspections ADD CONSTRAINT chk_ci_record_status CHECK (record_status IN ('DRAFT', 'FINALIZED', 'CANCELLED'));
ALTER TABLE container_inspections DROP CONSTRAINT IF EXISTS chk_ci_finalized_judged;
ALTER TABLE container_inspections ADD CONSTRAINT chk_ci_finalized_judged CHECK (record_status <> 'FINALIZED' OR overall_status IN ('PASS', 'FAIL'));
ALTER TABLE container_inspections DROP CONSTRAINT IF EXISTS chk_ci_cancel_fields;
ALTER TABLE container_inspections ADD CONSTRAINT chk_ci_cancel_fields CHECK (record_status <> 'CANCELLED' OR cancelled_at IS NOT NULL);

-- 같은 컨테이너에 작성 중(DRAFT) 기록은 1건 · 한 기록의 재점검은 동시에 1건 (서비스가 제약 이름으로 409 안내)
CREATE UNIQUE INDEX IF NOT EXISTS uk_ci_container_draft ON container_inspections (container_no) WHERE record_status = 'DRAFT' AND container_no IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ci_previous_active ON container_inspections (previous_inspection_id) WHERE record_status <> 'CANCELLED' AND previous_inspection_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_container_inspections_driver  ON container_inspections (driver_account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_container_inspections_order   ON container_inspections (order_id);
CREATE INDEX IF NOT EXISTS idx_container_inspections_company ON container_inspections (company_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_container_inspections_hub     ON container_inspections (hub_id, created_at DESC);

-- 2차 앱의 한 번 저장 점검(FINALIZED 기본값)은 확정 시각을 채운다
CREATE OR REPLACE FUNCTION scargo_ci_defaults() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    IF NEW.record_status = 'FINALIZED' AND NEW.finalized_at IS NULL THEN NEW.finalized_at := CURRENT_TIMESTAMP; END IF;
    IF NEW.created_by_account_id IS NULL THEN NEW.created_by_account_id := NEW.driver_account_id; END IF;
  END IF;
  NEW.updated_at := CURRENT_TIMESTAMP;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_ci_defaults ON container_inspections;
CREATE TRIGGER trg_ci_defaults BEFORE INSERT OR UPDATE ON container_inspections
    FOR EACH ROW EXECUTE FUNCTION scargo_ci_defaults();

-- 확정 · 취소본 보호: 확정본은 취소(사유 필수)만, 취소본은 변경 불가, 삭제 불가, 상태 되돌리기 불가
CREATE OR REPLACE FUNCTION scargo_ci_protect() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'DELETE' THEN
    RAISE EXCEPTION '컨테이너 점검 기록은 삭제할 수 없습니다 (취소 처리 사용, inspection_id=%)', OLD.inspection_id USING ERRCODE = '23514';
  END IF;
  IF OLD.record_status = 'CANCELLED' THEN
    RAISE EXCEPTION '취소된 점검 기록은 바꿀 수 없습니다 (inspection_id=%)', OLD.inspection_id USING ERRCODE = '23514';
  END IF;
  IF OLD.record_status = 'FINALIZED' THEN
    IF NEW.record_status = 'FINALIZED' AND (NEW.items IS DISTINCT FROM OLD.items OR NEW.overall_status IS DISTINCT FROM OLD.overall_status
        OR NEW.container_no IS DISTINCT FROM OLD.container_no OR NEW.memo IS DISTINCT FROM OLD.memo) THEN
      RAISE EXCEPTION '확정된 점검 기록은 바꿀 수 없습니다. 재점검을 이용하세요 (inspection_id=%)', OLD.inspection_id USING ERRCODE = '23514';
    END IF;
    IF NEW.record_status = 'DRAFT' THEN
      RAISE EXCEPTION '확정된 점검 기록을 작성 중으로 되돌릴 수 없습니다 (inspection_id=%)', OLD.inspection_id USING ERRCODE = '23514';
    END IF;
    IF NEW.record_status = 'CANCELLED' AND NULLIF(btrim(NEW.cancel_reason), '') IS NULL THEN
      RAISE EXCEPTION '확정된 점검 기록의 취소에는 사유가 필요합니다 (inspection_id=%)', OLD.inspection_id USING ERRCODE = '23514';
    END IF;
  END IF;
  RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS trg_ci_protect ON container_inspections;
CREATE TRIGGER trg_ci_protect BEFORE UPDATE OR DELETE ON container_inspections
    FOR EACH ROW EXECUTE FUNCTION scargo_ci_protect();
COMMENT ON TABLE container_inspections IS '컨테이너 점검표 (7-point). 웹 · 앱 작성(DRAFT → FINALIZED), 취소(CANCELLED), 재점검(previous_inspection_id)';

CREATE TABLE IF NOT EXISTS container_inspection_audits (
    audit_id         BIGSERIAL PRIMARY KEY,
    inspection_id    BIGINT NOT NULL REFERENCES container_inspections(inspection_id) ON DELETE RESTRICT,
    action           VARCHAR(20) NOT NULL CHECK (action IN ('CREATE', 'UPDATE', 'FINALIZE', 'CANCEL', 'REINSPECT')),
    actor_account_id BIGINT REFERENCES accounts(account_id) ON DELETE SET NULL,
    actor_user_id    VARCHAR(50),
    before_data      JSONB,
    after_data       JSONB,
    reason           TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ci_audits_inspection ON container_inspection_audits (inspection_id, created_at, audit_id);
COMMENT ON TABLE container_inspection_audits IS '컨테이너 점검표 변경 이력 (생성 · 수정 · 확정 · 취소 · 재점검)';

CREATE TABLE IF NOT EXISTS container_inspection_photos (
    photo_id               BIGSERIAL PRIMARY KEY,
    inspection_id          BIGINT NOT NULL REFERENCES container_inspections(inspection_id) ON DELETE RESTRICT,
    file_name              VARCHAR(255) NOT NULL,
    stored_name            VARCHAR(255) NOT NULL,
    content_type           VARCHAR(50) NOT NULL,
    size_bytes             BIGINT NOT NULL CHECK (size_bytes > 0),
    uploaded_by_account_id BIGINT REFERENCES accounts(account_id) ON DELETE SET NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_ci_photos_inspection ON container_inspection_photos (inspection_id, photo_id);
COMMENT ON TABLE container_inspection_photos IS '컨테이너 점검표 손상 사진 (파일은 scargo.upload-dir/inspections/{inspection_id}/)';

-- 5. 예외 · 사고 접수 -------------------------------------------------
CREATE TABLE IF NOT EXISTS ops_incidents (
    incident_id            BIGSERIAL PRIMARY KEY,
    incident_type          VARCHAR(20) NOT NULL CHECK (incident_type IN ('DAMAGE', 'SEAL', 'DELAY', 'VEHICLE', 'SAFETY', 'OTHER')),
    severity               VARCHAR(10) NOT NULL DEFAULT 'MEDIUM' CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH')),
    title                  VARCHAR(100) NOT NULL,
    detail                 TEXT,
    container_no           VARCHAR(11),
    order_id               BIGINT REFERENCES transport_orders(order_id) ON DELETE SET NULL,
    hub_id                 BIGINT REFERENCES hubs(hub_id) ON DELETE SET NULL,
    status                 VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
    assignee               VARCHAR(50),
    resolution             TEXT,
    reported_by_account_id BIGINT REFERENCES accounts(account_id) ON DELETE SET NULL,
    reported_by_user_id    VARCHAR(50),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at            TIMESTAMPTZ,
    CONSTRAINT chk_ops_incidents_resolved CHECK (status <> 'RESOLVED' OR (resolved_at IS NOT NULL AND NULLIF(btrim(resolution), '') IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_ops_incidents_status ON ops_incidents (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ops_incidents_hub ON ops_incidents (hub_id, created_at DESC);
COMMENT ON TABLE ops_incidents IS '운영 예외 · 사고 접수 (관리자 운영 센터)';

-- 6. 게이트 사전 예약 -------------------------------------------------
CREATE TABLE IF NOT EXISTS gate_reservations (
    reservation_id        BIGSERIAL PRIMARY KEY,
    company_id            BIGINT REFERENCES companies(company_id) ON DELETE SET NULL,
    vehicle_no            VARCHAR(20) NOT NULL,
    container_no          VARCHAR(11),
    direction             VARCHAR(3) NOT NULL CHECK (direction IN ('IN', 'OUT')),
    gate_id               BIGINT REFERENCES gates(gate_id) ON DELETE SET NULL,
    planned_at            TIMESTAMPTZ NOT NULL,
    status                VARCHAR(20) NOT NULL DEFAULT 'BOOKED' CHECK (status IN ('BOOKED', 'CANCELLED', 'CHECKED_IN', 'NO_SHOW')),
    memo                  VARCHAR(300),
    created_by_account_id BIGINT REFERENCES accounts(account_id) ON DELETE SET NULL,
    created_by_user_id    VARCHAR(50),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- 같은 차량이 같은 시각에 중복 예약 불가 (예약 상태만) → 서비스가 DuplicateKey 로 안내
CREATE UNIQUE INDEX IF NOT EXISTS uk_gate_reservations_vehicle_time ON gate_reservations (vehicle_no, planned_at) WHERE status = 'BOOKED';
CREATE INDEX IF NOT EXISTS idx_gate_reservations_planned ON gate_reservations (planned_at DESC);
CREATE INDEX IF NOT EXISTS idx_gate_reservations_company ON gate_reservations (company_id, planned_at DESC);
COMMENT ON TABLE gate_reservations IS '게이트 사전 예약 (반입 IN · 반출 OUT)';

-- 7. 지역센터 인수 확정자 (share 26.10.23 CenterReceiptService: ARRIVED → RECEIVED) -----------
ALTER TABLE shipment_legs ADD COLUMN IF NOT EXISTS received_by_account_id BIGINT;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_shipment_legs_received_by') THEN
    ALTER TABLE shipment_legs ADD CONSTRAINT fk_shipment_legs_received_by FOREIGN KEY (received_by_account_id) REFERENCES accounts(account_id) ON DELETE SET NULL;
  END IF;
END $$;
COMMENT ON COLUMN shipment_legs.received_by_account_id IS '지역센터 인수 확정한 관제사 (CENTER_OP)';

COMMIT;
