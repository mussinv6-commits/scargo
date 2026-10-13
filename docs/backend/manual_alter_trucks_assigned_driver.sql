-- 26.09.22 추가: 고정형(지입차) 기사-차량 배정 컬럼
-- Hibernate ddl-auto=update 로 자동 추가될 수도 있지만,
-- UNIQUE 제약조건은 update 모드에서 누락되는 경우가 있어 안전하게 수동으로도 실행 가능하도록 남겨둠.
-- 이미 컬럼/제약조건이 있다면(자동 생성됐다면) 에러 없이 무시하고 넘어가면 됩니다.

ALTER TABLE trucks
    ADD COLUMN IF NOT EXISTS assigned_account_id BIGINT UNIQUE;

ALTER TABLE trucks
    ADD CONSTRAINT fk_trucks_assigned_account
    FOREIGN KEY (assigned_account_id) REFERENCES accounts(account_id) ON DELETE SET NULL;

-- 26.09.22 추가: 차량 진입 허가 상태 컬럼
ALTER TABLE trucks
    ADD COLUMN IF NOT EXISTS entry_approval VARCHAR(20) NOT NULL DEFAULT 'PENDING';
