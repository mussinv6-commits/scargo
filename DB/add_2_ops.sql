-- 26.10.24 예외·사고 접수 (설치 SQL 을 넣은 DB 에 1회 실행, 다시 실행해도 안전)
-- 실행: psql -U postgres -d scargo_demo -f 추가_20261024_운영현황.sql
CREATE TABLE IF NOT EXISTS public.ops_incidents (
    incident_id bigserial PRIMARY KEY,
    incident_type varchar(20) NOT NULL,
    severity varchar(10) NOT NULL DEFAULT 'MEDIUM',
    title varchar(100) NOT NULL,
    detail text,
    container_no varchar(11),
    order_id bigint,
    hub_id bigint,
    status varchar(15) NOT NULL DEFAULT 'OPEN',
    assignee varchar(50),
    resolution text,
    reported_by_account_id bigint NOT NULL,
    reported_by_user_id varchar(50),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    resolved_at timestamptz,
    CONSTRAINT chk_incident_type CHECK (incident_type IN ('DAMAGE','SEAL','DELAY','VEHICLE','SAFETY','OTHER')),
    CONSTRAINT chk_incident_severity CHECK (severity IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT chk_incident_status CHECK (status IN ('OPEN','IN_PROGRESS','RESOLVED')),
    CONSTRAINT chk_incident_resolved CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_incidents_status ON public.ops_incidents (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_incidents_hub ON public.ops_incidents (hub_id);
COMMENT ON TABLE public.ops_incidents IS '예외·사고 접수 (파손/씰/지연/차량/안전/기타). 처리 상태 OPEN→IN_PROGRESS→RESOLVED';
