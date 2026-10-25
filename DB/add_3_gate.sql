-- 26.10.24 게이트 사전 예약 (설치 SQL 을 넣은 DB 에 1회 실행, 다시 실행해도 안전)
-- 실행: psql -U postgres -d scargo_demo -f 추가_20261024_게이트예약.sql
CREATE TABLE IF NOT EXISTS public.gate_reservations (
    reservation_id bigserial PRIMARY KEY,
    company_id bigint,
    vehicle_no varchar(20) NOT NULL,
    container_no varchar(11),
    direction varchar(3) NOT NULL DEFAULT 'IN',
    gate_id bigint,
    planned_at timestamptz NOT NULL,
    status varchar(12) NOT NULL DEFAULT 'BOOKED',
    memo varchar(300),
    created_by_account_id bigint NOT NULL,
    created_by_user_id varchar(50),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_gres_direction CHECK (direction IN ('IN','OUT')),
    CONSTRAINT chk_gres_status CHECK (status IN ('BOOKED','CHECKED_IN','CANCELLED','NO_SHOW'))
);
CREATE INDEX IF NOT EXISTS idx_gres_planned ON public.gate_reservations (planned_at);
CREATE INDEX IF NOT EXISTS idx_gres_company ON public.gate_reservations (company_id, planned_at DESC);
-- 같은 차량이 같은 시각에 예약 중복 방지
CREATE UNIQUE INDEX IF NOT EXISTS uk_gres_vehicle_time ON public.gate_reservations (vehicle_no, planned_at) WHERE status = 'BOOKED';
COMMENT ON TABLE public.gate_reservations IS '게이트 사전 예약(반입/반출 신청). BOOKED→CHECKED_IN/NO_SHOW/CANCELLED';
