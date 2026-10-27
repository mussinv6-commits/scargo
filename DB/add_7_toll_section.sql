-- ==================================================================
-- 팀 통합 DB 에 얹는 SCARGO 1구간(인천항 → 곤지암) 관제 추가분 — 여러 번 실행해도 안전
--   기준: 00_scargo_db_최종통합.sql (2026-10-21) 적용 후 실행. 팀 테이블·뷰·트리거는 수정·삭제하지 않는다 (컬럼 추가와 새 뷰만).
--   팀 방식(통행 키 = 입장 통과 기록 번호 trip_passage_id, 결제 테이블 없음 · 요금은 toll_passages.fare_won)을 그대로 두고,
--   관제 화면용 통행 묶음 키(trip_key)와 요금 근거 컬럼을 병행해서 추가한다.
--   [추가] 컬럼 3개(overload_checks.trip_key · toll_passages.trip_key, fare_discount_pct) · 새 테이블 6개(toll_fare_rates · toll_trip_quotes · departure_logs · toll_route_events · toll_trip_positions · toll_control_actions)
--          · 뷰 6개(v_toll_night_exclusion + v_toll_leg1_* 5개) · 계량 지점 2곳 · 요금표 10행
--   [제외] toll_payments · v_toll_payments · 결제 트리거 — 팀이 의도적으로 없앤 것이라 가져오지 않음
-- ==================================================================
BEGIN;
SELECT pg_advisory_xact_lock(20261023,19800);

-- 1. 컬럼 ----------------------------------------------------------
ALTER TABLE overload_checks
    ADD COLUMN IF NOT EXISTS trip_key        BIGINT;        -- 관제 통행 묶음 키 = 그 통행 첫 검사의 check_id
CREATE INDEX IF NOT EXISTS idx_overload_trip_key ON overload_checks (trip_key, check_id);
COMMENT ON COLUMN overload_checks.trip_key IS '관제 화면용 통행 묶음 키(첫 검사의 check_id). 팀의 trip_passage_id 와 별개';

ALTER TABLE toll_passages
    ADD COLUMN IF NOT EXISTS trip_key          BIGINT,         -- overload_checks.trip_key 와 같은 값
    ADD COLUMN IF NOT EXISTS fare_discount_pct SMALLINT;       -- 요금(fare_won)에 적용된 심야할인 % (0 / 30 / 50)
DO $$ BEGIN
    ALTER TABLE toll_passages ADD CONSTRAINT chk_toll_passage_fare_discount
        CHECK (fare_discount_pct IS NULL OR fare_discount_pct BETWEEN 0 AND 100);
EXCEPTION WHEN duplicate_object THEN NULL; END $$;
CREATE INDEX IF NOT EXISTS idx_toll_passages_trip_key ON toll_passages (trip_key);

-- 2. 차종별 통행료 기준표 (남인천 · 청계, 1~5종). 팀의 toll_fee_rules(km당 요금 규칙)와는 별개 -----------
CREATE TABLE IF NOT EXISTS toll_fare_rates (
    rate_id           BIGSERIAL PRIMARY KEY,
    section_code      VARCHAR(30)  NOT NULL,                      -- NAMINCHEON_TG / CHEONGGYE_TG (개방식). 폐쇄식 구간이 생기면 entry_tollgate_id 를 채운다
    section_name      VARCHAR(100) NOT NULL,
    toll_system       VARCHAR(10)  NOT NULL CHECK (toll_system IN ('OPEN','CLOSED')),
    entry_tollgate_id BIGINT REFERENCES tollgates(tollgate_id),   -- 폐쇄식 입구 (개방식은 NULL)
    exit_tollgate_id  BIGINT NOT NULL REFERENCES tollgates(tollgate_id),
    vehicle_class     SMALLINT     NOT NULL CHECK (vehicle_class BETWEEN 1 AND 5),
    amount            INTEGER      NOT NULL CHECK (amount >= 0),  -- 할인 전 기준 요금(원)
    fare_basis        VARCHAR(10)  NOT NULL CHECK (fare_basis IN ('OFFICIAL','ESTIMATE')),
    basis_note        TEXT,
    effective_date    DATE,
    UNIQUE (section_code, vehicle_class)
);
COMMENT ON TABLE toll_fare_rates IS '구간·차종별 통행료 기준표(할인 전). OFFICIAL=도로공사 조회 공시값, ESTIMATE=추정값';
INSERT INTO toll_fare_rates (section_code, section_name, toll_system, entry_tollgate_id, exit_tollgate_id, vehicle_class, amount, fare_basis, basis_note, effective_date)
SELECT v.sec, v.nm, v.sys, ent.tollgate_id, ext.tollgate_id, v.cls, v.amt, 'OFFICIAL', '한국도로공사 통행요금 조회(2026.10.22)', DATE '2026-10-22'
  FROM (VALUES
     ('NAMINCHEON_TG', '남인천TG (개방식)', 'OPEN', NULL, '053', 1, 900), ('NAMINCHEON_TG', '남인천TG (개방식)', 'OPEN', NULL, '053', 2, 900),
     ('NAMINCHEON_TG', '남인천TG (개방식)', 'OPEN', NULL, '053', 3, 900), ('NAMINCHEON_TG', '남인천TG (개방식)', 'OPEN', NULL, '053', 4, 1000),
     ('NAMINCHEON_TG', '남인천TG (개방식)', 'OPEN', NULL, '053', 5, 1000),
     ('CHEONGGYE_TG',  '청계TG (개방식)',  'OPEN', NULL, 'SCARGO-PPT-CHEONGGYE', 1, 1000), ('CHEONGGYE_TG', '청계TG (개방식)', 'OPEN', NULL, 'SCARGO-PPT-CHEONGGYE', 2, 1000),
     ('CHEONGGYE_TG',  '청계TG (개방식)',  'OPEN', NULL, 'SCARGO-PPT-CHEONGGYE', 3, 1000), ('CHEONGGYE_TG', '청계TG (개방식)', 'OPEN', NULL, 'SCARGO-PPT-CHEONGGYE', 4, 1100),
     ('CHEONGGYE_TG',  '청계TG (개방식)',  'OPEN', NULL, 'SCARGO-PPT-CHEONGGYE', 5, 1200)
  ) AS v(sec, nm, sys, ent_code, ext_code, cls, amt)
  LEFT JOIN tollgates ent ON ent.tollgate_code = v.ent_code
  JOIN tollgates ext ON ext.tollgate_code = v.ext_code
ON CONFLICT (section_code, vehicle_class) DO NOTHING;

-- 3. 계량 지점 (남인천 053 · 청계). 청계 좌표는 경로선 기준 값 --------------------------------
--    청계TG(수도권제1순환)는 8장에서 좌표 없이 만들어 둔 행(SCARGO-PPT-CHEONGGYE)을 쓴다. 좌표가 비어 있을 때만 경로선 기준 추정값을 채움.
UPDATE tollgates SET latitude = 37.3933323, longitude = 127.0305782,
       address = COALESCE(address, '경기도 의왕시 청계동 (좌표는 기본 경로선 기준 추정, 실제 위치 확인 필요)'),
       sido = COALESCE(sido, '경기도'), sigungu = COALESCE(sigungu, '의왕시')
 WHERE tollgate_code = 'SCARGO-PPT-CHEONGGYE' AND (latitude IS NULL OR longitude IS NULL);
INSERT INTO weigh_stations (station_code, station_name, address, tollgate_id, road_name, latitude, longitude, is_active, sido, sigungu, alert_radius_m)
SELECT 'TOLL-053', t.tollgate_name || 'TG 톨게이트 계량', t.address, t.tollgate_id, t.road_name, t.latitude, t.longitude, TRUE, t.sido, t.sigungu, 2000
  FROM tollgates t WHERE t.tollgate_code = '053'
ON CONFLICT (station_code) DO NOTHING;
INSERT INTO weigh_stations (station_code, station_name, address, tollgate_id, road_name, latitude, longitude, is_active, sido, sigungu, alert_radius_m)
SELECT 'TOLL-CHEONGGYE', t.tollgate_name || 'TG 톨게이트 계량', t.address, t.tollgate_id, t.road_name, t.latitude, t.longitude, TRUE, t.sido, t.sigungu, 2000
  FROM tollgates t WHERE t.tollgate_code = 'SCARGO-PPT-CHEONGGYE' AND t.latitude IS NOT NULL
ON CONFLICT (station_code) DO NOTHING;

-- 4. 통행 1건의 예상 통행료 · 심야할인 제외 판정 --------------------------------------------
-- 3. 통행 1건의 예상 통행료 (출발 직전 계산) ------------------------------
--    lines: 요금 구간별 내역 [{section, system, base, discount_pct, amount, night_ratio, window_start, window_end, basis}, ...]
--    알림 예: "예상 통행료 1,100원 (기준 2,200원, 심야할인 50%)"  /  "예상 통행료 1,600원 (기준 2,200원, 심야할인 남인천 0% · 청계 50%)"
CREATE TABLE IF NOT EXISTS toll_trip_quotes (
    trip_key         BIGINT PRIMARY KEY,                          -- overload_checks.trip_key
    vehicle_no       VARCHAR(20) NOT NULL REFERENCES trucks(vehicle_no),
    vehicle_class    SMALLINT    NOT NULL,
    departed_at      TIMESTAMP   NOT NULL,                        -- 출발 시각 (현지 시각)
    expected_minutes INTEGER     NOT NULL,                        -- 예상 소요시간(분). 지점별 통과 시각 = 출발 + 소요 × (지점 km ÷ 전체 km)
    base_total       INTEGER     NOT NULL,                        -- 할인 전 합계(원)
    expected_total   INTEGER     NOT NULL,                        -- 할인 후 예상 합계(원)
    has_estimate     BOOLEAN     NOT NULL DEFAULT FALSE,          -- 추정 요금 포함 여부
    night_excluded   BOOLEAN     NOT NULL DEFAULT FALSE,          -- 1년 이내 과태료 부과 2회 이상으로 심야할인 제외
    fined_trips_1y   INTEGER     NOT NULL DEFAULT 0,              -- 제외 판정에 쓴 최근 1년 과태료 부과 통행 수
    lines            JSONB       NOT NULL DEFAULT '[]'::jsonb,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE toll_trip_quotes IS '통행 1건의 예상 통행료와 심야할인 산정 근거 (구간별 내역은 lines)';
-- 5. 심야할인 제외 판정: 최근 1년 안에 과태료가 부과된 통행(=단속) 수. 2회 이상이면 제외 ------------
CREATE OR REPLACE VIEW v_toll_night_exclusion AS
SELECT t.vehicle_no,
       COUNT(c.check_id) FILTER (WHERE c.fine_manwon > 0 AND c.checked_at >= CURRENT_TIMESTAMP - INTERVAL '1 year') AS fined_trips_1y,
       COUNT(c.check_id) FILTER (WHERE c.fine_manwon > 0 AND c.checked_at >= CURRENT_TIMESTAMP - INTERVAL '1 year') >= 2 AS excluded
  FROM trucks t LEFT JOIN overload_checks c ON c.vehicle_no = t.vehicle_no
 GROUP BY t.vehicle_no;
COMMENT ON VIEW v_toll_night_exclusion IS '화물차 심야할인 제외 대상: 1년 이내 과적 2회 이상 단속(과태료 부과 통행 기준)';

-- 5. 출차 기록 · 경로 이벤트 · 현재 위치 · 관제 조치 (10장) --------------------------------
-- 1. 인천항 출차 OCR 기록 (통행의 시작점). trip_key 는 첫 계측이 생기면 채움 ---------------------
CREATE TABLE IF NOT EXISTS departure_logs (
    departure_id        BIGSERIAL PRIMARY KEY,
    departed_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recognized_plate_no VARCHAR(30),                              -- OCR 이 읽은 번호
    vehicle_no          VARCHAR(20) REFERENCES trucks(vehicle_no), -- 등록 차량이면 채움
    plate_confidence    NUMERIC(5,2),
    result              VARCHAR(15) NOT NULL,                     -- DEPARTED 출차 / OCR_FAILED 인식 실패 / UNREGISTERED 미등록
    plate_crop_url      TEXT,
    front_image_url     TEXT,
    trip_key            BIGINT,                                   -- overload_checks.trip_key (DEPARTED 인 경우)
    CONSTRAINT chk_departure_result CHECK (result IN ('DEPARTED','OCR_FAILED','UNREGISTERED'))
);
CREATE INDEX IF NOT EXISTS idx_departure_logs_time ON departure_logs (departed_at DESC);
CREATE INDEX IF NOT EXISTS idx_departure_logs_trip ON departure_logs (trip_key);
COMMENT ON TABLE departure_logs IS '인천항 출차 OCR 기록. 톨게이트·검문소에서는 OCR 을 하지 않고 이 기록의 차량 번호로 도로공사 계측 결과를 매칭한다';

-- 2. 경로 이벤트 (출발 · 이탈 · 복귀 · 검문소 전방 경고 · 도착) -------------------------------
CREATE TABLE IF NOT EXISTS toll_route_events (
    event_id    BIGSERIAL PRIMARY KEY,
    trip_key    BIGINT      NOT NULL,
    seq         INTEGER     NOT NULL,                             -- 통행 안에서의 순서
    kind        VARCHAR(20) NOT NULL,
    level       VARCHAR(8)  NOT NULL DEFAULT 'info',              -- info / warn / ok
    km          NUMERIC(5,1),                                     -- 기본 경로선 위 진행 거리
    text        TEXT        NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_route_event_kind CHECK (kind IN ('START','DEVIATE','TOLL','CHECKPOINT_WARN','CHECKPOINT_PASS','RETURN','ARRIVE','HALT')),
    CONSTRAINT uq_route_event UNIQUE (trip_key, seq)
);
CREATE INDEX IF NOT EXISTS idx_route_events_trip ON toll_route_events (trip_key, seq);
COMMENT ON TABLE toll_route_events IS '관제 지도(W-14)가 감지한 경로 이탈·복귀·검문소 전방 경고 이벤트. 이탈 기준: 경로선 100m 이상 2회 연속, 복귀 50m 이내';

-- 3. 통행별 현재 위치 (1통행 1행, 계속 덮어씀) -------------------------------------------
CREATE TABLE IF NOT EXISTS toll_trip_positions (
    trip_key      BIGINT PRIMARY KEY,
    km            NUMERIC(5,1) NOT NULL DEFAULT 0,                -- 기본 경로선 위 진행 거리 (이탈 중이면 가장 가까운 지점)
    lat           NUMERIC(9,6),
    lng           NUMERIC(9,6),
    route_status  VARCHAR(10) NOT NULL DEFAULT 'NORMAL',          -- NORMAL 정상 주행 / DEVIATING 경로 이탈 / STOPPED 정차 / ARRIVED 도착
    dist_m        INTEGER NOT NULL DEFAULT 0,                     -- 경로선까지 거리(m)
    route_type    VARCHAR(10) NOT NULL DEFAULT 'BASIC',           -- 경로 종류: BASIC 기본 / DETOUR 안산 우회
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_trip_pos_status CHECK (route_status IN ('NORMAL','DEVIATING','STOPPED','ARRIVED'))
);
COMMENT ON TABLE toll_trip_positions IS '관제 대상 차량의 현재 위치. W-13 목록과 W-14 지도가 같은 값을 본다';

-- 4. 관제자 조치: 확정 판정이 나오면 시스템이 권고를 만들고, 관제자가 지시를 보내면 한 행이 남는다 ---------
CREATE TABLE IF NOT EXISTS toll_control_actions (
    action_id       BIGSERIAL PRIMARY KEY,
    trip_key        BIGINT      NOT NULL,
    vehicle_no      VARCHAR(20) NOT NULL REFERENCES trucks(vehicle_no),
    action_type     VARCHAR(20) NOT NULL,
    basis_point     VARCHAR(20),                                  -- 근거 지점 (v_toll_points.point_code)
    basis_check_id  BIGINT,                                       -- 근거 계측 (overload_checks.check_id)
    message         TEXT        NOT NULL,                         -- 운전자에게 보낸 안내 문구
    issued_by       VARCHAR(50) NOT NULL DEFAULT '관제자',
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivery_status VARCHAR(10) NOT NULL DEFAULT 'SENT',
    CONSTRAINT chk_control_action_type CHECK (action_type IN ('EXIT_ADVISE','HOLD_NOTICE','CAUTION_NOTICE','CHECKPOINT_NOTICE','REROUTE_ADVISE')),
    CONSTRAINT chk_control_action_status CHECK (delivery_status IN ('SENT','ACKED','FAILED'))
);
CREATE INDEX IF NOT EXISTS idx_control_actions_trip ON toll_control_actions (trip_key, issued_at);
COMMENT ON TABLE toll_control_actions IS '관제자가 운전자에게 보낸 지시/안내 기록. EXIT_ADVISE 진출 권고 · HOLD_NOTICE 검문소 조치 확인 요청 · CAUTION_NOTICE 적재 점검 안내 · CHECKPOINT_NOTICE 검문소 전방 안내 · REROUTE_ADVISE 대안 경로 안내(경로 이탈 차량). 단속·강제 진출은 도로공사·경찰 권한이라 우리는 안내만 한다';

-- 이미 10장을 실행해 둔 DB 를 위해: 조치 종류에 REROUTE_ADVISE(대안 경로 안내)를 추가 (여러 번 실행해도 안전)
ALTER TABLE toll_control_actions DROP CONSTRAINT IF EXISTS chk_control_action_type;
ALTER TABLE toll_control_actions ADD CONSTRAINT chk_control_action_type
    CHECK (action_type IN ('EXIT_ADVISE','HOLD_NOTICE','CAUTION_NOTICE','CHECKPOINT_NOTICE','REROUTE_ADVISE'));

-- 5. 예상 통행료에 경로 변경 내용 추가 (이탈로 톨게이트를 지나지 않으면 예상 통행료를 다시 계산) -------------
ALTER TABLE toll_trip_quotes
    ADD COLUMN IF NOT EXISTS route_type    VARCHAR(10) NOT NULL DEFAULT 'BASIC',   -- BASIC / DETOUR
    ADD COLUMN IF NOT EXISTS reroute_total INTEGER,                                 -- 경로 이탈 후 다시 계산한 예상 통행료(원). NULL 이면 변경 없음
    ADD COLUMN IF NOT EXISTS reroute_note  TEXT,                                    -- 예: 남인천TG 미통과
    ADD COLUMN IF NOT EXISTS skipped_sections JSONB NOT NULL DEFAULT '[]'::jsonb;   -- 지나지 않는 요금 구간 코드들


-- 6. 1구간 지점 3곳 (팀의 v_toll_points 는 그대로 두고 별도 뷰) ----------------------
CREATE OR REPLACE VIEW v_toll_leg1_points AS
SELECT c.point_code, c.seq, c.point_name, c.kind, c.toll_role, c.km_from_start, c.retest_max,
       ws.station_id, ws.station_code, ws.tollgate_id
  FROM (VALUES
        ('CK18',          1, '인천 과적검문소', 'CHECKPOINT', NULL::text, 2.5::numeric,  1, 'CHECKPOINT-18'),
        ('TG_NAMINCHEON', 2, '남인천TG',        'TOLLGATE',   'OPEN',    18.6,           0, 'TOLL-053'),
        ('TG_CHEONGGYE',  3, '청계TG',          'TOLLGATE',   'OPEN',    47.5,           0, 'TOLL-CHEONGGYE')
       ) AS c(point_code, seq, point_name, kind, toll_role, km_from_start, retest_max, station_code)
  JOIN weigh_stations ws ON ws.station_code = c.station_code;
COMMENT ON VIEW v_toll_leg1_points IS '기본 경로 1구간 지점 3곳 (검문소 18 + 개방식 톨게이트 2). 도로공사·경찰 연계 계측 결과 기준';

-- 7. 통행 1건당 1행 (현재 위치·상태·요금) ------------------------------------
CREATE OR REPLACE VIEW v_toll_leg1_trip_current AS
SELECT f.trip_key                           AS trip_id,
       f.vehicle_no,
       f.checked_at                         AS entered_at,
       dl.plate_confidence,
       dl.plate_crop_url,
       lc.check_id                          AS last_check_id,
       lp.point_code                        AS last_point,
       lp.point_name                        AS last_point_name,
       lp.seq                               AS last_seq,
       lp.km_from_start                     AS last_km,
       lc.total_weight_kg                   AS last_weight_kg,
       lc.is_violation                      AS last_violation,
       lc.retry_count                       AS last_retry,
       CASE
         WHEN lc.is_passed = FALSE AND lc.retry_count >= lp.retest_max THEN 'HALTED'
         WHEN lc.is_passed = FALSE THEN 'RETESTING'
         WHEN lp.seq = (SELECT MAX(seq) FROM v_toll_leg1_points) THEN 'COMPLETED'
         ELSE 'RUNNING'
       END                                  AS status,
       COALESCE(fn.fine_manwon, 0)          AS fine_manwon,
       COALESCE(pd.paid_won, 0)             AS paid_won,
       q.vehicle_class, q.expected_total, q.base_total, q.has_estimate, q.night_excluded,
       lc.checked_at                        AS updated_at
  FROM (SELECT DISTINCT ON (trip_key) trip_key, vehicle_no, checked_at
          FROM overload_checks WHERE trip_key IS NOT NULL ORDER BY trip_key, check_id) f
  LEFT JOIN departure_logs dl ON dl.trip_key = f.trip_key
  JOIN LATERAL (SELECT c.* FROM overload_checks c WHERE c.trip_key = f.trip_key ORDER BY c.check_id DESC LIMIT 1) lc ON TRUE
  JOIN v_toll_leg1_points lp ON lp.station_id = lc.station_id
  LEFT JOIN LATERAL (SELECT SUM(c.fine_manwon) AS fine_manwon FROM overload_checks c WHERE c.trip_key = f.trip_key) fn ON TRUE
  LEFT JOIN LATERAL (SELECT SUM(tp.fare_won) AS paid_won FROM toll_passages tp
                      WHERE tp.trip_key = f.trip_key AND tp.passage_status = 'PASSED') pd ON TRUE
  LEFT JOIN toll_trip_quotes q ON q.trip_key = f.trip_key;

-- 8. 톨게이트 통과 기록 (탭 = point_code) ----------------------------------
CREATE OR REPLACE VIEW v_toll_leg1_gate_log AS
SELECT p.passage_id, p.trip_key, p.detected_at, t.tollgate_name,
       vp.point_code, vp.toll_role, (vp.toll_role = 'CLOSED_EXIT') AS is_exit,
       p.recognized_plate_no, p.vehicle_no, p.plate_confidence, p.plate_crop_url,
       p.recognition_status, p.passage_status, p.block_reason,
       c.total_weight_kg, c.is_violation, c.violation_reason, c.fine_manwon, c.is_reoffense,
       p.fare_won, p.fare_discount_pct AS discount_pct
  FROM toll_passages p
  JOIN tollgates t ON t.tollgate_id = p.tollgate_id
  JOIN v_toll_leg1_points vp ON vp.tollgate_id = p.tollgate_id
  LEFT JOIN LATERAL (
        SELECT c.* FROM overload_checks c
         WHERE c.trip_key = p.trip_key AND c.station_id = vp.station_id
         ORDER BY c.check_id DESC LIMIT 1) c ON TRUE
 WHERE p.trip_key IS NOT NULL OR p.recognition_status <> 'SUCCESS';

-- 9. 계측별 판정(정상/주의/확정) --------------------------------------------
CREATE OR REPLACE VIEW v_toll_leg1_check_level AS
SELECT c.check_id, c.trip_key, p.point_code, p.point_name, p.kind, c.retry_count, c.checked_at, c.total_weight_kg,
       GREATEST(c.axle1_weight, c.axle2_weight, c.axle3_weight, c.axle4_weight, c.axle5_weight, c.axle6_weight, c.axle7_weight, c.axle8_weight) AS max_axle_kg,
       lim.limit_kg,
       CASE WHEN c.is_violation THEN 'CONFIRMED'
            WHEN c.total_weight_kg > lim.limit_kg
              OR GREATEST(c.axle1_weight, c.axle2_weight, c.axle3_weight, c.axle4_weight, c.axle5_weight, c.axle6_weight, c.axle7_weight, c.axle8_weight) > 10000 THEN 'CAUTION'
            ELSE 'NORMAL' END AS level,
       c.check_id = (SELECT MAX(c2.check_id) FROM overload_checks c2 WHERE c2.trip_key = c.trip_key AND c2.station_id = c.station_id) AS is_final
  FROM overload_checks c
  JOIN v_toll_leg1_points p ON p.station_id = c.station_id
  LEFT JOIN vehicle_specs vs ON vs.vehicle_no = c.vehicle_no
 CROSS JOIN LATERAL (SELECT LEAST(40000, COALESCE(NULLIF(vs.gross_weight_kg, 0), vs.empty_weight_kg + vs.max_payload_kg, 40000)) AS limit_kg) lim
 WHERE c.trip_key IS NOT NULL;

-- 10. 통행 요약 (운행 중 / 정차 / 운행 완료) -------------------------------------
CREATE OR REPLACE VIEW v_toll_leg1_trip_summary AS
SELECT t.trip_id, t.vehicle_no, q.departed_at, arr.arrived_at,
       CASE pos.route_status WHEN 'ARRIVED' THEN 'COMPLETED' WHEN 'STOPPED' THEN 'STOPPED' ELSE 'RUNNING' END AS trip_state,
       q.route_type, q.expected_total, q.reroute_total, t.paid_won, t.fine_manwon,
       (SELECT COUNT(*) FROM toll_route_events e WHERE e.trip_key = t.trip_id AND e.kind = 'DEVIATE') AS deviate_cnt,
       (SELECT COUNT(*) FROM toll_control_actions a WHERE a.trip_key = t.trip_id) AS action_cnt,
       COALESCE((SELECT CASE MAX(CASE l.level WHEN 'CONFIRMED' THEN 2 WHEN 'CAUTION' THEN 1 ELSE 0 END) WHEN 2 THEN 'CONFIRMED' WHEN 1 THEN 'CAUTION' ELSE 'NORMAL' END
                   FROM v_toll_leg1_check_level l WHERE l.trip_key = t.trip_id AND l.is_final), 'NORMAL') AS final_level
  FROM v_toll_leg1_trip_current t
  LEFT JOIN toll_trip_quotes q ON q.trip_key = t.trip_id
  LEFT JOIN toll_trip_positions pos ON pos.trip_key = t.trip_id
  LEFT JOIN LATERAL (SELECT MIN(e.occurred_at) AS arrived_at FROM toll_route_events e WHERE e.trip_key = t.trip_id AND e.kind = 'ARRIVE') arr ON TRUE;
COMMIT;
