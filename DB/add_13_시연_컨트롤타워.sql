-- add_13_시연_컨트롤타워.sql : 공통 컨트롤타워 · 허브 · 물류센터 관제 화면 시연 데이터 (여러 번 실행해도 중복되지 않음)
-- 전제: add_5 / add_6 / add_12 적용 후. 모든 시연 행에는 '[시연]' 표시가 붙는다.
SET client_min_messages = warning;
BEGIN;

-- 1) 진행 중 운송오더: 도착 예정 시각(eta)·기사·화주 채우기 (관제 현황 · 운송망 관제 · 허브 입고 예정)
UPDATE transport_orders SET eta = now() + interval '40 minutes' WHERE eta IS NULL AND status IN ('CREATED','ASSIGNED','LOADED','GATE_OUT','IN_TRANSIT') AND order_id = (SELECT min(order_id) FROM transport_orders WHERE eta IS NULL AND status IN ('CREATED','ASSIGNED','LOADED','GATE_OUT','IN_TRANSIT'));
UPDATE transport_orders SET eta = now() + interval '90 minutes' WHERE eta IS NULL AND status IN ('CREATED','ASSIGNED','LOADED','GATE_OUT','IN_TRANSIT');
UPDATE transport_orders SET driver_account_id = 1 WHERE driver_account_id IS NULL AND status IN ('ASSIGNED','LOADED','GATE_OUT','IN_TRANSIT') AND vehicle_no IS NOT NULL;
UPDATE transport_orders SET shipper_name = '[시연] 한솔무역' WHERE shipper_name IS NULL AND status NOT IN ('HANDED_OVER','CANCELED');

-- 2) 진행 중 지선 운행(허브→센터): 목록 맨 위(최신) 20건만 시연용으로 채운다 - 기사 · 출발 시각 · 적재 계획 · 국내 봉인
CREATE TEMP TABLE _demo_legs ON COMMIT DROP AS
  SELECT assignment_id, row_number() OVER (ORDER BY assignment_id DESC) AS rn
    FROM (SELECT assignment_id FROM shipment_legs WHERE leg_type = 'HUB_TO_CENTER' AND received_at IS NULL AND status <> 'CANCELED'
           ORDER BY assignment_id DESC LIMIT 20) q;
UPDATE shipment_legs l SET driver_account_id = COALESCE(l.driver_account_id, (SELECT min(account_id) FROM accounts WHERE user_type = 'BRANCH_DRIVER')),
       planned_units = COALESCE(l.planned_units, 6)
  FROM _demo_legs d WHERE d.assignment_id = l.assignment_id;
UPDATE shipment_legs l SET planned_depart_at = now() + (d.rn * interval '12 minutes')
  FROM _demo_legs d WHERE d.assignment_id = l.assignment_id AND l.status = 'ASSIGNED' AND l.departed_at IS NULL AND l.planned_depart_at IS NULL;
UPDATE shipment_legs l SET departed_at = COALESCE(l.departed_at, now() - interval '50 minutes')
  FROM _demo_legs d WHERE d.assignment_id = l.assignment_id AND l.status IN ('IN_TRANSIT', 'ARRIVED');
UPDATE shipment_legs l SET arrived_at = COALESCE(l.arrived_at, now() - interval '10 minutes')
  FROM _demo_legs d WHERE d.assignment_id = l.assignment_id AND l.status = 'ARRIVED';
UPDATE shipment_legs l SET domestic_seal_no = 'DS-' || lpad(l.assignment_id::text, 4, '0')
  FROM _demo_legs d WHERE d.assignment_id = l.assignment_id AND l.departed_at IS NOT NULL AND l.domestic_seal_no IS NULL;

-- 3) 지선 롤테이너(branch_units): 위 운행마다 6개 (출발한 운행은 전부 적재, 배차 지시는 일부만)
INSERT INTO branch_units (unit_id, assignment_id, dest_center_id, status, loaded_at)
SELECT 'RT-' || l.assignment_id || '-' || n, l.assignment_id, l.to_center_id,
       CASE WHEN l.departed_at IS NOT NULL OR n <= 3 THEN 'LOADED' ELSE 'SORTING' END,
       CASE WHEN l.departed_at IS NOT NULL OR n <= 3 THEN now() - interval '20 minutes' END
  FROM shipment_legs l JOIN _demo_legs d ON d.assignment_id = l.assignment_id, generate_series(1, 6) n
ON CONFLICT (unit_id) DO NOTHING;

-- 4) 센터 도착 봉인 대조(센터별 관제): 도착한 운행
INSERT INTO branch_seal_checks (assignment_id, observed_seal_no, expected_seal_no, matches, actor_name)
SELECT l.assignment_id, l.domestic_seal_no, l.domestic_seal_no, true, '[시연] 센터 검수'
  FROM shipment_legs l JOIN _demo_legs d ON d.assignment_id = l.assignment_id
 WHERE l.status = 'ARRIVED' AND l.domestic_seal_no IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM branch_seal_checks c WHERE c.assignment_id = l.assignment_id);

-- 5) 이상 상황 (거점별 · 도로 위)
INSERT INTO exceptions (exception_type, severity, center_id, message, status, occurred_at)
SELECT v.t, v.sv, v.c, v.m, 'OPEN', now() - v.ago::interval
  FROM (VALUES
    ('SEAL_MISMATCH', 'CRITICAL', 1, '[시연] 곤지암허브 도크 D1 · 씰 불일치 (개봉 보류) SCGU1234565', '15 minutes'),
    ('OVERLOAD',      'CRITICAL', NULL::bigint, '[시연] 경남81아5921 · 과적 의심 (도로 위 계측)', '40 minutes'),
    ('SORT_FAILED',   'WARNING',  1, '[시연] 허브 분류 실패 2건 · 재스캔 필요', '55 minutes'),
    ('ITEM_DAMAGED',  'WARNING',  3, '[시연] 인천 물류센터 · 박스 파손 1건', '70 minutes'),
    ('SEAL_DAMAGED',  'CRITICAL', 5, '[시연] 서울 물류센터 · 지선 봉인 훼손', '95 minutes'),
    ('OTHER',         'WARNING',  4, '[시연] 경기 물류센터 · 하차 지연 30분', '120 minutes')
  ) AS v(t, sv, c, m, ago)
 WHERE NOT EXISTS (SELECT 1 FROM exceptions e WHERE e.message = v.m);
INSERT INTO exceptions (exception_type, severity, center_id, message, status, occurred_at, resolved_at, resolution_note, assignee_account_id)
SELECT 'OTHER', 'WARNING', 1, '[시연] 허브 게이트 카메라 재부팅', 'RESOLVED', now() - interval '3 hours', now() - interval '2 hours', '재부팅 후 정상 확인', 4
 WHERE NOT EXISTS (SELECT 1 FROM exceptions e WHERE e.message = '[시연] 허브 게이트 카메라 재부팅');
UPDATE exceptions SET status = 'IN_PROGRESS', assignee_account_id = 4 WHERE message LIKE '[시연] 서울 물류센터%' AND status = 'OPEN';

-- 6) 점검표: 화물차 차량 점검 (오늘)
INSERT INTO vehicle_checklist (vehicle_no, inspector_account_id, inspection_date, brake_status, tire_status, light_status, overall_status, memo)
SELECT t.vehicle_no, 1, current_date, 'PASS', CASE WHEN n = 4 THEN 'FAIL' ELSE 'PASS' END, 'PASS', CASE WHEN n = 4 THEN 'FAIL' ELSE 'PASS' END,
       CASE WHEN n = 4 THEN '[시연] 타이어 마모 · 교체 필요' ELSE '[시연] 이상 없음' END
  FROM (SELECT vehicle_no, row_number() OVER (ORDER BY vehicle_no) n FROM trucks
         WHERE vehicle_no IN (SELECT vehicle_no FROM transport_orders WHERE status NOT IN ('HANDED_OVER','CANCELED') AND vehicle_no IS NOT NULL)
            OR vehicle_no IN (SELECT vehicle_no FROM shipment_legs WHERE received_at IS NULL AND vehicle_no IS NOT NULL)) t
 WHERE n <= 8 AND NOT EXISTS (SELECT 1 FROM vehicle_checklist c WHERE c.vehicle_no = t.vehicle_no AND c.inspection_date = current_date);

-- 7) 허브 게이트 OCR · 씰 확인: 입차 화물차(씰 상태별) + 출차 택배차
INSERT INTO hub_vehicle_logs (hub_id, direction, recognized_plate, photo_plate, vehicle_kind, decision, lane, dock_no, ocr_confidence, ocr_engine, seal_status, seal_input, created_at)
SELECT 1, v.d, v.p, v.p, v.k, 'ALLOW', v.ln, 0, v.cf, 'SYNTHETIC-DEMO', v.ss, v.si, now() - v.ago::interval
  FROM (VALUES
    ('ENTRY', '서울85자6013', 'CARGO',   'IN',  98.0, 'OK',       'SC-1001', '12 minutes'),
    ('ENTRY', '경기92아5439', 'CARGO',   'IN',  96.5, 'PENDING',  NULL,      '8 minutes'),
    ('ENTRY', '부산91아4739', 'CARGO',   'IN',  94.2, 'MISMATCH', 'SC-9999', '25 minutes'),
    ('ENTRY', '인천98아2578', 'CARGO',   'IN',  97.1, 'OK',       'SC-1002', '40 minutes'),
    ('EXIT',  '인천98바2578', 'COURIER', 'OUT', 95.0, NULL,       NULL,      '18 minutes'),
    ('EXIT',  '대구80자6850', 'COURIER', 'OUT', 96.8, NULL,       NULL,      '33 minutes'),
    ('EXIT',  '경기96사1256', 'COURIER', 'OUT', 93.9, NULL,       NULL,      '47 minutes')
  ) AS v(d, p, k, ln, cf, ss, si, ago)
 WHERE NOT EXISTS (SELECT 1 FROM hub_vehicle_logs h WHERE h.recognized_plate = v.p AND h.direction = v.d AND h.created_at > now() - interval '12 hours');

COMMIT;
