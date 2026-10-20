-- ============================================================
-- 26.10.14 2차 프로젝트: 허브(AMR 디지털 트윈) 테이블 병합 SQL
--  - 기존 scargo DB(PostgreSQL)에 "추가만" 합니다. 기존 테이블은 건드리지 않음
--  - 여러 번 실행해도 안전하도록 IF NOT EXISTS / ON CONFLICT 사용
--  - 1차와 연결 키: trucks.vehicle_no(화물차 번호판), containers.container_no
--
--  흐름: 화물차 허브 도착(입고) → 운송장 OCR → AMR이 권역별 랙 보관
--        → 출고 도크 → 권역(인천 · 서울 · 경기)행 택배차 출발
--  예지보전: AMR 센서 수집 → AI 고장 예측 → 예비기 교대 → 계획 정비
-- ============================================================

-- ============================================================
-- 1. hubs (허브 마스터)
-- ============================================================
CREATE TABLE IF NOT EXISTS hubs (
    hub_id BIGSERIAL PRIMARY KEY,                              -- 허브 고유 ID
    hub_code VARCHAR(30) NOT NULL UNIQUE,                      -- 허브 코드 (예: HUB-ICH-01)
    hub_name VARCHAR(100) NOT NULL,                            -- 허브 이름
    company_id BIGINT,                                         -- 운영 업체 ID
    address TEXT,                                              -- 주소
    latitude NUMERIC(10, 7),                                   -- 위도 (컨트롤타워 지도용)
    longitude NUMERIC(10, 7),                                  -- 경도
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_hubs_company FOREIGN KEY (company_id)
        REFERENCES companies(company_id) ON DELETE SET NULL
);

-- ============================================================
-- 2. delivery_regions (출고 권역: 인천 · 서울 · 경기)
-- ============================================================
CREATE TABLE IF NOT EXISTS delivery_regions (
    region_id BIGSERIAL PRIMARY KEY,                           -- 권역 고유 ID
    region_code VARCHAR(10) NOT NULL UNIQUE,                   -- 권역 코드 (ICN, SEL, GGI)
    region_name VARCHAR(30) NOT NULL,                          -- 권역 이름 (인천, 서울, 경기)
    color_hex VARCHAR(7),                                      -- 3D/화면 표시 색
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
-- 3. hub_zones (허브 내부 보관 구역: A 인천 · B 서울 · C 경기 · D 냉장)
-- ============================================================
CREATE TABLE IF NOT EXISTS hub_zones (
    zone_id BIGSERIAL PRIMARY KEY,                             -- 구역 고유 ID
    hub_id BIGINT NOT NULL,                                    -- 소속 허브
    zone_tag VARCHAR(5) NOT NULL,                              -- 구역 기호 (A, B, C, D)
    zone_name VARCHAR(30) NOT NULL,                            -- 구역 이름
    region_id BIGINT,                                          -- 이 구역 화물의 출고 권역 (냉장은 NULL)
    is_cold BOOLEAN NOT NULL DEFAULT FALSE,                    -- 냉장 · 냉동 창고 여부
    rack_row INT NOT NULL,                                     -- 시뮬레이션 랙 줄 번호 (0~3)

    CONSTRAINT fk_zones_hub FOREIGN KEY (hub_id) REFERENCES hubs(hub_id) ON DELETE CASCADE,
    CONSTRAINT fk_zones_region FOREIGN KEY (region_id) REFERENCES delivery_regions(region_id),
    CONSTRAINT uk_zones_hub_tag UNIQUE (hub_id, zone_tag)
);

-- ============================================================
-- 4. hub_rack_slots (랙 칸: 0단 = AMR 바닥 칸, 1~3단 = 무인 지게차)
-- ============================================================
CREATE TABLE IF NOT EXISTS hub_rack_slots (
    slot_id BIGSERIAL PRIMARY KEY,                             -- 랙 칸 고유 ID
    zone_id BIGINT NOT NULL,                                   -- 소속 구역
    side_no INT NOT NULL,                                      -- 랙 면 (0: 북쪽, 1: 남쪽)
    slot_index INT NOT NULL,                                   -- 칸 번호 (0~8)
    level_no INT NOT NULL DEFAULT 0,                           -- 단 (0~3)
    slot_label VARCHAR(20) NOT NULL,                           -- 표시용 라벨 (예: A-03-0단)
    status VARCHAR(20) NOT NULL DEFAULT 'EMPTY',               -- 칸 상태
    pos_x NUMERIC(7, 2),                                       -- 평면도 X (1000 x 640 기준)
    pos_y NUMERIC(7, 2),                                       -- 평면도 Y
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_slots_zone FOREIGN KEY (zone_id) REFERENCES hub_zones(zone_id) ON DELETE CASCADE,
    CONSTRAINT uk_slots_position UNIQUE (zone_id, side_no, slot_index, level_no),
    CONSTRAINT chk_slots_level CHECK (level_no BETWEEN 0 AND 3),
    CONSTRAINT chk_slots_status CHECK (status IN ('EMPTY', 'OCCUPIED', 'RESERVED_IN', 'RESERVED_OUT', 'DISABLED'))
);

-- ============================================================
-- 5. hub_docks (입고 · 출고 도크)
-- ============================================================
CREATE TABLE IF NOT EXISTS hub_docks (
    dock_id BIGSERIAL PRIMARY KEY,                             -- 도크 고유 ID
    hub_id BIGINT NOT NULL,                                    -- 소속 허브
    dock_code VARCHAR(10) NOT NULL,                            -- 도크 코드 (D1~D6)
    dock_type VARCHAR(10) NOT NULL,                            -- 'IN' 입고 / 'OUT' 출고
    region_id BIGINT,                                          -- 출고 도크의 권역 (입고 도크는 NULL)
    status VARCHAR(20) NOT NULL DEFAULT 'EMPTY',               -- 도크 상태
    capacity INT NOT NULL DEFAULT 4,                           -- 택배차 1대 적재 건수
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_docks_hub FOREIGN KEY (hub_id) REFERENCES hubs(hub_id) ON DELETE CASCADE,
    CONSTRAINT fk_docks_region FOREIGN KEY (region_id) REFERENCES delivery_regions(region_id),
    CONSTRAINT uk_docks_hub_code UNIQUE (hub_id, dock_code),
    CONSTRAINT chk_docks_type CHECK (dock_type IN ('IN', 'OUT')),
    CONSTRAINT chk_docks_status CHECK (status IN ('EMPTY', 'ARRIVING', 'DOCKED', 'LEAVING'))
);

-- ============================================================
-- 6. hub_inbound_receipts (화물차 허브 도착 · 하차) ← 1차와 연결되는 지점
-- ============================================================
CREATE TABLE IF NOT EXISTS hub_inbound_receipts (
    receipt_id BIGSERIAL PRIMARY KEY,                          -- 입고 접수 ID
    hub_id BIGINT NOT NULL,                                    -- 도착 허브
    dock_id BIGINT,                                            -- 하차 도크
    vehicle_no VARCHAR(20),                                    -- 화물차 번호판 (1차 trucks)
    container_no VARCHAR(11),                                  -- 컨테이너 번호 (1차 containers)
    parcel_count INT NOT NULL DEFAULT 0,                       -- 하차 화물 건수
    status VARCHAR(20) NOT NULL DEFAULT 'ARRIVED',             -- 접수 상태
    arrived_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    unloaded_at TIMESTAMP WITH TIME ZONE,                      -- 하차 완료 시각

    CONSTRAINT fk_receipts_hub FOREIGN KEY (hub_id) REFERENCES hubs(hub_id),
    CONSTRAINT fk_receipts_dock FOREIGN KEY (dock_id) REFERENCES hub_docks(dock_id) ON DELETE SET NULL,
    CONSTRAINT fk_receipts_truck FOREIGN KEY (vehicle_no) REFERENCES trucks(vehicle_no) ON DELETE SET NULL,
    CONSTRAINT fk_receipts_container FOREIGN KEY (container_no) REFERENCES containers(container_no) ON DELETE SET NULL,
    CONSTRAINT chk_receipts_status CHECK (status IN ('ARRIVED', 'UNLOADING', 'COMPLETED'))
);

-- ============================================================
-- 7. hub_dispatches (권역행 택배차 출고)
-- ============================================================
CREATE TABLE IF NOT EXISTS hub_dispatches (
    dispatch_id BIGSERIAL PRIMARY KEY,                         -- 출고 ID
    hub_id BIGINT NOT NULL,                                    -- 출발 허브
    dock_id BIGINT,                                            -- 출고 도크
    region_id BIGINT NOT NULL,                                 -- 도착 권역
    delivery_vehicle_no VARCHAR(20),                           -- 택배차 번호판
    parcel_count INT NOT NULL DEFAULT 0,                       -- 적재 건수
    total_weight_kg NUMERIC(8, 1) NOT NULL DEFAULT 0,          -- 총 중량 (kg)
    departed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_dispatches_hub FOREIGN KEY (hub_id) REFERENCES hubs(hub_id),
    CONSTRAINT fk_dispatches_dock FOREIGN KEY (dock_id) REFERENCES hub_docks(dock_id) ON DELETE SET NULL,
    CONSTRAINT fk_dispatches_region FOREIGN KEY (region_id) REFERENCES delivery_regions(region_id)
);

-- ============================================================
-- 8. parcels (화물 박스 · 운송장 단위)
-- ============================================================
CREATE TABLE IF NOT EXISTS parcels (
    parcel_no VARCHAR(20) PRIMARY KEY,                         -- 운송장 번호 (예: SC-24001)
    hub_id BIGINT NOT NULL,                                    -- 현재 허브
    receipt_id BIGINT,                                         -- 입고 접수 (어느 화물차로 왔는지)
    region_id BIGINT,                                          -- 목적지 권역 (OCR 결과)
    category VARCHAR(30),                                      -- 품목 (전자제품, 식품, 의류 ...)
    weight_kg NUMERIC(6, 1) NOT NULL,                          -- 무게 (kg)
    size_class CHAR(1),                                        -- 크기 S / M / L
    handling VARCHAR(20) NOT NULL DEFAULT '일반',               -- 취급 표시
    status VARCHAR(20) NOT NULL DEFAULT 'IN_TRUCK',            -- 화물 상태
    slot_id BIGINT,                                            -- 현재 보관 랙 칸
    dispatch_id BIGINT,                                        -- 출고된 택배차
    inbound_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    stored_at TIMESTAMP WITH TIME ZONE,                        -- 랙 보관 시각
    outbound_at TIMESTAMP WITH TIME ZONE,                      -- 출고 시각

    CONSTRAINT fk_parcels_hub FOREIGN KEY (hub_id) REFERENCES hubs(hub_id),
    CONSTRAINT fk_parcels_receipt FOREIGN KEY (receipt_id) REFERENCES hub_inbound_receipts(receipt_id) ON DELETE SET NULL,
    CONSTRAINT fk_parcels_region FOREIGN KEY (region_id) REFERENCES delivery_regions(region_id),
    CONSTRAINT fk_parcels_slot FOREIGN KEY (slot_id) REFERENCES hub_rack_slots(slot_id) ON DELETE SET NULL,
    CONSTRAINT fk_parcels_dispatch FOREIGN KEY (dispatch_id) REFERENCES hub_dispatches(dispatch_id) ON DELETE SET NULL,
    CONSTRAINT chk_parcels_size CHECK (size_class IN ('S', 'M', 'L')),
    CONSTRAINT chk_parcels_handling CHECK (handling IN ('일반', '파손주의', '취급주의', '냉장', '중량물')),
    CONSTRAINT chk_parcels_status CHECK (status IN ('IN_TRUCK', 'MOVING', 'STORED', 'OUTBOUND_READY', 'SHIPPED'))
);

-- ============================================================
-- 9. waybill_scans (입고 도크 운송장 OCR 기록)
-- ============================================================
CREATE TABLE IF NOT EXISTS waybill_scans (
    scan_id BIGSERIAL PRIMARY KEY,                             -- 스캔 ID
    parcel_no VARCHAR(20),                                     -- 매칭된 운송장
    dock_id BIGINT,                                            -- 스캔한 도크
    recognized_text VARCHAR(100),                              -- OCR 원문
    region_id BIGINT,                                          -- 인식된 목적지 권역
    confidence NUMERIC(5, 2),                                  -- 신뢰도 (0.00 ~ 100.00%)
    is_success BOOLEAN NOT NULL DEFAULT TRUE,                  -- 인식 성공 여부
    image_url TEXT,                                            -- 스캔 이미지
    scanned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_scans_parcel FOREIGN KEY (parcel_no) REFERENCES parcels(parcel_no) ON DELETE SET NULL,
    CONSTRAINT fk_scans_dock FOREIGN KEY (dock_id) REFERENCES hub_docks(dock_id) ON DELETE SET NULL,
    CONSTRAINT fk_scans_region FOREIGN KEY (region_id) REFERENCES delivery_regions(region_id)
);

-- ============================================================
-- 10. amr_robots (AMR · 무인 지게차 마스터 + 현재 상태)
-- ============================================================
CREATE TABLE IF NOT EXISTS amr_robots (
    robot_id BIGSERIAL PRIMARY KEY,                            -- 로봇 고유 ID
    hub_id BIGINT NOT NULL,                                    -- 배치 허브
    robot_code VARCHAR(20) NOT NULL UNIQUE,                    -- 코드 (AMR-01, FL-01)
    robot_type VARCHAR(20) NOT NULL DEFAULT 'AMR',             -- 'AMR' / 'FORKLIFT'
    robot_role VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',          -- 'ACTIVE' 가동 / 'SPARE' 예비
    state VARCHAR(20) NOT NULL DEFAULT 'IDLE',                 -- 현재 작업 상태
    battery_pct NUMERIC(5, 2),                                 -- 배터리 (%)
    health_score NUMERIC(5, 2),                                -- 건강 점수 (0~100)
    cum_load_kg NUMERIC(10, 1) NOT NULL DEFAULT 0,             -- 정비 후 누적 운반 중량 (kg)
    home_x NUMERIC(7, 2),                                      -- 충전 · 대기 위치 X
    home_y NUMERIC(7, 2),                                      -- 충전 · 대기 위치 Y
    max_payload_kg NUMERIC(7, 1),                              -- 최대 적재 중량
    installed_at DATE,                                         -- 도입일
    last_maintained_at TIMESTAMP WITH TIME ZONE,               -- 마지막 정비
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_robots_hub FOREIGN KEY (hub_id) REFERENCES hubs(hub_id),
    CONSTRAINT chk_robots_type CHECK (robot_type IN ('AMR', 'FORKLIFT')),
    CONSTRAINT chk_robots_role CHECK (robot_role IN ('ACTIVE', 'SPARE')),
    CONSTRAINT chk_robots_state CHECK (state IN ('IDLE', 'TO_PICK', 'LOADING', 'TO_DROP', 'UNLOADING',
                                                 'TO_MAINT', 'MAINT', 'TO_HOME', 'TO_RACK', 'FORK_WORK', 'CHARGING'))
);

-- ============================================================
-- 11. amr_telemetry (센서 시계열: 진동 · 온도 · 배터리 · 위치)
-- ============================================================
CREATE TABLE IF NOT EXISTS amr_telemetry (
    telemetry_id BIGSERIAL PRIMARY KEY,                        -- 측정 ID
    robot_id BIGINT NOT NULL,                                  -- 로봇
    vibration_mm_s NUMERIC(5, 2),                              -- 구동모터 진동 (mm/s, 정상 1.2~2.0)
    motor_temp_c NUMERIC(5, 2),                                -- 모터 온도 (℃)
    battery_pct NUMERIC(5, 2),                                 -- 배터리 (%)
    health_score NUMERIC(5, 2),                                -- 건강 점수
    cum_load_kg NUMERIC(10, 1),                                -- 누적 운반 중량
    speed_m_s NUMERIC(5, 2),                                   -- 속도
    pos_x NUMERIC(7, 2),                                       -- 평면도 X
    pos_y NUMERIC(7, 2),                                       -- 평면도 Y
    state VARCHAR(20),                                         -- 측정 시점 상태
    recorded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_telemetry_robot FOREIGN KEY (robot_id) REFERENCES amr_robots(robot_id) ON DELETE CASCADE
);

-- ============================================================
-- 12. amr_tasks (작업 지시: 입고 · 출고 · 보충 · 적치)
-- ============================================================
CREATE TABLE IF NOT EXISTS amr_tasks (
    task_id BIGSERIAL PRIMARY KEY,                             -- 작업 ID
    robot_id BIGINT,                                           -- 담당 로봇
    task_type VARCHAR(20) NOT NULL,                            -- 작업 종류
    parcel_no VARCHAR(20),                                     -- 대상 화물
    from_dock_id BIGINT,                                       -- 출발 도크 (입고)
    to_dock_id BIGINT,                                         -- 도착 도크 (출고)
    from_slot_id BIGINT,                                       -- 출발 랙 칸
    to_slot_id BIGINT,                                         -- 도착 랙 칸
    status VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',            -- 작업 상태
    assigned_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_tasks_robot FOREIGN KEY (robot_id) REFERENCES amr_robots(robot_id) ON DELETE SET NULL,
    CONSTRAINT fk_tasks_parcel FOREIGN KEY (parcel_no) REFERENCES parcels(parcel_no) ON DELETE SET NULL,
    CONSTRAINT fk_tasks_from_dock FOREIGN KEY (from_dock_id) REFERENCES hub_docks(dock_id) ON DELETE SET NULL,
    CONSTRAINT fk_tasks_to_dock FOREIGN KEY (to_dock_id) REFERENCES hub_docks(dock_id) ON DELETE SET NULL,
    CONSTRAINT fk_tasks_from_slot FOREIGN KEY (from_slot_id) REFERENCES hub_rack_slots(slot_id) ON DELETE SET NULL,
    CONSTRAINT fk_tasks_to_slot FOREIGN KEY (to_slot_id) REFERENCES hub_rack_slots(slot_id) ON DELETE SET NULL,
    CONSTRAINT chk_tasks_type CHECK (task_type IN ('INBOUND', 'OUTBOUND', 'REPLENISH', 'STOW', 'MAINT_MOVE')),
    CONSTRAINT chk_tasks_status CHECK (status IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELED'))
);

-- ============================================================
-- 13. amr_predictions (AI 고장 예측 결과)
-- ============================================================
CREATE TABLE IF NOT EXISTS amr_predictions (
    prediction_id BIGSERIAL PRIMARY KEY,                       -- 예측 ID
    robot_id BIGINT NOT NULL,                                  -- 대상 로봇
    component VARCHAR(50) NOT NULL,                            -- 부품 (구동모터 베어링 등)
    risk_level VARCHAR(10) NOT NULL,                           -- 위험도
    predicted_rul_hours NUMERIC(7, 1),                         -- 예상 잔여 수명 (시간)
    vibration_slope NUMERIC(8, 4),                             -- 진동 증가 기울기
    cum_load_kg NUMERIC(10, 1),                                -- 예측 시점 누적 운반 중량
    reason TEXT,                                               -- 예측 근거
    model_version VARCHAR(30),                                 -- 모델 버전
    is_resolved BOOLEAN NOT NULL DEFAULT FALSE,                -- 정비로 해소 여부
    predicted_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_predictions_robot FOREIGN KEY (robot_id) REFERENCES amr_robots(robot_id) ON DELETE CASCADE,
    CONSTRAINT chk_predictions_risk CHECK (risk_level IN ('LOW', 'WARN', 'CRIT'))
);

-- ============================================================
-- 14. amr_maintenance (정비 이력 · 예비기 교대)
-- ============================================================
CREATE TABLE IF NOT EXISTS amr_maintenance (
    maint_id BIGSERIAL PRIMARY KEY,                            -- 정비 ID
    robot_id BIGINT NOT NULL,                                  -- 정비 대상 로봇
    prediction_id BIGINT,                                      -- 근거가 된 예측 (계획 정비)
    spare_robot_id BIGINT,                                     -- 대신 투입된 예비기
    maint_type VARCHAR(20) NOT NULL DEFAULT 'PLANNED',         -- 'PLANNED' 계획 / 'CORRECTIVE' 고장 후
    part_name VARCHAR(50),                                     -- 교체 부품
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',           -- 진행 상태
    worker_account_id BIGINT,                                  -- 담당 정비 인력 (accounts)
    memo TEXT,
    scheduled_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_maint_robot FOREIGN KEY (robot_id) REFERENCES amr_robots(robot_id) ON DELETE CASCADE,
    CONSTRAINT fk_maint_prediction FOREIGN KEY (prediction_id) REFERENCES amr_predictions(prediction_id) ON DELETE SET NULL,
    CONSTRAINT fk_maint_spare FOREIGN KEY (spare_robot_id) REFERENCES amr_robots(robot_id) ON DELETE SET NULL,
    CONSTRAINT fk_maint_worker FOREIGN KEY (worker_account_id) REFERENCES accounts(account_id) ON DELETE SET NULL,
    CONSTRAINT chk_maint_type CHECK (maint_type IN ('PLANNED', 'CORRECTIVE')),
    CONSTRAINT chk_maint_status CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'DONE', 'CANCELED'))
);

-- ============================================================
-- 15. hub_events (관제 이벤트 로그 · 화면 알림)
-- ============================================================
CREATE TABLE IF NOT EXISTS hub_events (
    event_id BIGSERIAL PRIMARY KEY,                            -- 이벤트 ID
    hub_id BIGINT NOT NULL,                                    -- 허브
    robot_id BIGINT,                                           -- 관련 로봇 (없으면 NULL)
    event_level VARCHAR(10) NOT NULL DEFAULT 'INFO',           -- 등급
    phase_no INT,                                              -- 시나리오 단계 (1~5)
    message TEXT NOT NULL,                                     -- 내용
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_events_hub FOREIGN KEY (hub_id) REFERENCES hubs(hub_id) ON DELETE CASCADE,
    CONSTRAINT fk_events_robot FOREIGN KEY (robot_id) REFERENCES amr_robots(robot_id) ON DELETE SET NULL,
    CONSTRAINT chk_events_level CHECK (event_level IN ('INFO', 'OK', 'WARN', 'CRIT', 'DISPATCH'))
);

-- ============================================================
-- 16. 인덱스
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_slots_zone_status ON hub_rack_slots(zone_id, status);
CREATE INDEX IF NOT EXISTS idx_receipts_vehicle ON hub_inbound_receipts(vehicle_no, arrived_at DESC);
CREATE INDEX IF NOT EXISTS idx_receipts_container ON hub_inbound_receipts(container_no);
CREATE INDEX IF NOT EXISTS idx_parcels_status ON parcels(hub_id, status);
CREATE INDEX IF NOT EXISTS idx_parcels_region ON parcels(region_id);
CREATE INDEX IF NOT EXISTS idx_parcels_receipt ON parcels(receipt_id);
CREATE INDEX IF NOT EXISTS idx_scans_time ON waybill_scans(scanned_at DESC);
CREATE INDEX IF NOT EXISTS idx_dispatches_region_time ON hub_dispatches(region_id, departed_at DESC);
CREATE INDEX IF NOT EXISTS idx_telemetry_robot_time ON amr_telemetry(robot_id, recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_tasks_robot_status ON amr_tasks(robot_id, status);
CREATE INDEX IF NOT EXISTS idx_predictions_open ON amr_predictions(robot_id) WHERE is_resolved = FALSE;
CREATE INDEX IF NOT EXISTS idx_maint_robot ON amr_maintenance(robot_id, scheduled_at DESC);
CREATE INDEX IF NOT EXISTS idx_events_hub_time ON hub_events(hub_id, created_at DESC);

-- ============================================================
-- 17. 기본 데이터 (허브 · 권역 · 구역 · 랙 칸 · 도크 · 로봇)
-- ============================================================
INSERT INTO hubs (hub_code, hub_name, company_id, address, latitude, longitude)
SELECT 'HUB-ICH-01', 'S카고 이천 허브', c.company_id, '경기도 이천시 (가상 허브)', 37.2410000, 127.3760000
FROM (SELECT MIN(company_id) AS company_id FROM companies WHERE company_name = '못먹어도 물류센터') c
ON CONFLICT (hub_code) DO NOTHING;

INSERT INTO delivery_regions (region_code, region_name, color_hex) VALUES
    ('ICN', '인천', '#b48cff'),
    ('SEL', '서울', '#5aa8ff'),
    ('GGI', '경기', '#ff7eb3')
ON CONFLICT (region_code) DO NOTHING;

-- 구역: 랙 0줄 = D 냉장, 1줄 = A 인천, 2줄 = B 서울, 3줄 = C 경기 (시뮬레이션 ZONES 순서와 동일)
INSERT INTO hub_zones (hub_id, zone_tag, zone_name, region_id, is_cold, rack_row)
SELECT h.hub_id, z.tag, z.name, r.region_id, z.cold, z.row_no
FROM hubs h
CROSS JOIN (VALUES ('D', '냉장', NULL, TRUE, 0),
                   ('A', '인천', 'ICN', FALSE, 1),
                   ('B', '서울', 'SEL', FALSE, 2),
                   ('C', '경기', 'GGI', FALSE, 3)) AS z(tag, name, region_code, cold, row_no)
LEFT JOIN delivery_regions r ON r.region_code = z.region_code
WHERE h.hub_code = 'HUB-ICH-01'
ON CONFLICT (hub_id, zone_tag) DO NOTHING;

-- 랙 칸: 구역마다 2면 x 9칸 x 4단 (냉장 창고는 북쪽 1면만) → 총 252칸
-- 좌표 = 시뮬레이션 SX(i) = 262 + i*49, AISLES = [62, 175, 295, 415, 540]
INSERT INTO hub_rack_slots (zone_id, side_no, slot_index, level_no, slot_label, pos_x, pos_y)
SELECT z.zone_id, s.side_no, i.idx, lv.level_no,
       z.zone_tag || '-' || LPAD((i.idx + 1)::text, 2, '0') || '-' || s.side_no || '면-' || lv.level_no || '단',
       262 + i.idx * 49,
       (ARRAY[62, 175, 295, 415, 540])[z.rack_row + s.side_no + 1]
FROM hub_zones z
CROSS JOIN (VALUES (0), (1)) AS s(side_no)
CROSS JOIN generate_series(0, 8) AS i(idx)
CROSS JOIN generate_series(0, 3) AS lv(level_no)
JOIN hubs h ON h.hub_id = z.hub_id AND h.hub_code = 'HUB-ICH-01'
WHERE NOT (z.is_cold AND s.side_no = 1)
ON CONFLICT (zone_id, side_no, slot_index, level_no) DO NOTHING;

-- 도크: D1~D3 입고, D4 인천 · D5 서울 · D6 경기 출고
INSERT INTO hub_docks (hub_id, dock_code, dock_type, region_id, status)
SELECT h.hub_id, d.code, d.dtype, r.region_id, 'DOCKED'
FROM hubs h
CROSS JOIN (VALUES ('D1', 'IN', NULL), ('D2', 'IN', NULL), ('D3', 'IN', NULL),
                   ('D4', 'OUT', 'ICN'), ('D5', 'OUT', 'SEL'), ('D6', 'OUT', 'GGI')) AS d(code, dtype, region_code)
LEFT JOIN delivery_regions r ON r.region_code = d.region_code
WHERE h.hub_code = 'HUB-ICH-01'
ON CONFLICT (hub_id, dock_code) DO NOTHING;

-- 로봇: AMR 4대 가동 + AMR-05 예비 + 무인 지게차 FL-01 (시뮬레이션 초기값과 동일)
INSERT INTO amr_robots (hub_id, robot_code, robot_type, robot_role, state, battery_pct, health_score,
                        cum_load_kg, home_x, home_y, max_payload_kg, installed_at)
SELECT h.hub_id, r.code, r.rtype, r.rrole, 'IDLE', r.batt, r.health, r.cum, r.hx, 600, r.payload, DATE '2026-03-02'
FROM hubs h
CROSS JOIN (VALUES ('AMR-01', 'AMR', 'ACTIVE', 66, 98, 8200, 262, 300),
                   ('AMR-02', 'AMR', 'ACTIVE', 72, 98, 9100, 311, 300),
                   ('AMR-03', 'AMR', 'ACTIVE', 78, 98, 12400, 360, 300),
                   ('AMR-04', 'AMR', 'ACTIVE', 84, 98, 7600, 409, 300),
                   ('AMR-05', 'AMR', 'SPARE', 90, 98, 3100, 458, 300),
                   ('FL-01', 'FORKLIFT', 'ACTIVE', 82, 97, 5300, 660, 1500))
     AS r(code, rtype, rrole, batt, health, cum, hx, payload)
WHERE h.hub_code = 'HUB-ICH-01'
ON CONFLICT (robot_code) DO NOTHING;

-- ============================================================
-- 18. (선택) 시연용 샘플: AMR-03 진동 상승 → 예측 → 계획 정비 이력
--      그래프 · 이력 화면 확인용. 필요 없으면 이 블록은 실행하지 않아도 됨
-- ============================================================
INSERT INTO amr_telemetry (robot_id, vibration_mm_s, motor_temp_c, battery_pct, health_score, cum_load_kg, state, recorded_at)
SELECT r.robot_id,
       ROUND((1.55 + GREATEST(0, g - 30) * 0.05)::numeric, 2),
       ROUND((45 + GREATEST(0, g - 30) * 0.25)::numeric, 2),
       ROUND((78 - g * 0.2)::numeric, 2),
       ROUND(LEAST(99, 100 - GREATEST(0, (1.55 + GREATEST(0, g - 30) * 0.05) - 1.9) * 15)::numeric, 2),
       12400 + g * 12,
       'TO_DROP',
       CURRENT_TIMESTAMP - ((60 - g) * INTERVAL '1 minute')
FROM amr_robots r
CROSS JOIN generate_series(0, 59) AS g
WHERE r.robot_code = 'AMR-03'
  AND NOT EXISTS (SELECT 1 FROM amr_telemetry t WHERE t.robot_id = r.robot_id);

INSERT INTO amr_predictions (robot_id, component, risk_level, predicted_rul_hours, vibration_slope, cum_load_kg, reason, model_version)
SELECT r.robot_id, '구동모터 베어링', 'CRIT', 18, 0.0500, 13108,
       '진동 1.2~2.0 mm/s 정상 범위 초과 후 상승 추세 · 누적 운반 13.1t', 'trend-v1'
FROM amr_robots r
WHERE r.robot_code = 'AMR-03'
  AND NOT EXISTS (SELECT 1 FROM amr_predictions p WHERE p.robot_id = r.robot_id);

INSERT INTO amr_maintenance (robot_id, prediction_id, spare_robot_id, maint_type, part_name, status, memo)
SELECT t.robot_id, p.prediction_id, s.robot_id, 'PLANNED', '구동모터 베어링', 'SCHEDULED', '예비기 AMR-05 투입 후 계획 정비'
FROM amr_robots t
JOIN amr_predictions p ON p.robot_id = t.robot_id
JOIN amr_robots s ON s.robot_code = 'AMR-05'
WHERE t.robot_code = 'AMR-03'
  AND NOT EXISTS (SELECT 1 FROM amr_maintenance m WHERE m.robot_id = t.robot_id);

-- ============================================================
-- 19. (선택) 알림 유형에 AMR_ALERT 추가
--      ※ 백엔드 Enum/NotificationType.java 에도 AMR_ALERT 를 같이 추가해야 사용 가능
-- ============================================================
-- ALTER TABLE notifications DROP CONSTRAINT IF EXISTS chk_notification_type;
-- ALTER TABLE notifications ADD CONSTRAINT chk_notification_type
--     CHECK (notification_type IN ('CORPORATE_APPROVAL', 'OVERLOAD_WARNING', 'NOTICE', 'SYSTEM', 'TIRE_ALERT', 'AMR_ALERT'));
