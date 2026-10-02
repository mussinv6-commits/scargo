-- ============================================================
-- 26.09.30 백엔드 병합(scargo_260928 + 백엔드.zip) 후 DB 반영용 SQL
-- ddl-auto: update 로는 "기존 컬럼 삭제 / NOT NULL 해제 / CHECK 제약 변경" 이 안 되기 때문에
-- 서버 기동 전에 한 번 직접 실행해 주세요. (DB: cargo)
-- ============================================================

-- 1) 게이트 마스터 테이블 (created_at 을 DB 기본값으로 채우는 구조라 DEFAULT 필요)
CREATE TABLE IF NOT EXISTS gates (
    gate_id BIGSERIAL PRIMARY KEY,
    gate_code VARCHAR(50) NOT NULL UNIQUE,
    gate_name VARCHAR(100) NOT NULL,
    gate_type VARCHAR(10) NOT NULL,          -- 'IN', 'OUT', 'BOTH'
    latitude NUMERIC(10, 7),
    longitude NUMERIC(10, 7),
    location_description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2) gate_logs: gate_name/gate_type 문자열 컬럼 → gates(gate_id) 외래키로 변경
--    ※ 기존 gate_logs 데이터가 있으면 gate_id 를 채울 수 없으므로 테스트 데이터는 비우고 진행
DELETE FROM gate_logs;
ALTER TABLE gate_logs DROP CONSTRAINT IF EXISTS chk_gate_type;
ALTER TABLE gate_logs DROP COLUMN IF EXISTS gate_name;
ALTER TABLE gate_logs DROP COLUMN IF EXISTS gate_type;
ALTER TABLE gate_logs ADD COLUMN IF NOT EXISTS gate_id BIGINT;
ALTER TABLE gate_logs ALTER COLUMN gate_id SET NOT NULL;
ALTER TABLE gate_logs DROP CONSTRAINT IF EXISTS fk_gate_logs_gate;
ALTER TABLE gate_logs ADD CONSTRAINT fk_gate_logs_gate
    FOREIGN KEY (gate_id) REFERENCES gates(gate_id);

-- 3) 알림 유형에 TIRE_ALERT(타이어 점검/교체 주기 알림) 추가
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS chk_notification_type;
ALTER TABLE notifications ADD CONSTRAINT chk_notification_type
    CHECK (notification_type IN ('CORPORATE_APPROVAL', 'OVERLOAD_WARNING', 'NOTICE', 'SYSTEM', 'TIRE_ALERT'));

-- 4) (선택) Firebase 를 완전히 안 쓸 거라면 FCM 토큰 테이블 삭제
-- DROP TABLE IF EXISTS fcm_tokens;
