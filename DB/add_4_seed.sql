-- 26.10.24 시연용 데이터 (최근 14일 운송·하역·점검 + 지금 확인할 경보 상황). 설치 SQL 과 운영현황 · 게이트예약 SQL 을 먼저 넣은 DB 에 1회 실행.
-- 실행: psql -U postgres -d scargo_demo -f 추가_20261024_시연데이터.sql      (다시 실행해도 SEED- 로 시작하는 건이 있으면 건너뜀)
-- 지우기: 추가_20261024_시연데이터_삭제.sql
DO $seed$
DECLARE
  trucks_arr text[]; conts_arr text[]; comps int[] := ARRAY[1,1,1,6,14,15,16,17]; drivers int[] := ARRAY[1,8,9];
  i int := 0; d int; k int; n_per int; base timestamptz; dep timestamptz; arr timestamptz; eta_t timestamptz;
  v text; c text; comp int; drv int; sid bigint; aid bigint; late boolean;
  docks int[] := ARRAY[4,5,6]; seq int := 0;
BEGIN
  IF EXISTS (SELECT 1 FROM transport_orders WHERE order_no LIKE 'SEED-%') THEN
    RAISE NOTICE '시연 데이터가 이미 있어 건너뜁니다.'; RETURN;
  END IF;
  SELECT array_agg(vehicle_no ORDER BY vehicle_no) INTO trucks_arr FROM trucks
   WHERE vehicle_category = 'CONTAINER_TRUCK' AND vehicle_no NOT IN ('서울85자6013','경기92아5439','경기90자8118');
  SELECT array_agg(container_no ORDER BY container_no) INTO conts_arr FROM containers
   WHERE container_no NOT IN ('SCGU1234565','SCGU3000020','SCGU2000010');

  -- (1) 과거 14일 인계 완료 오더 + 하역 완료 입고 기록
  FOR d IN REVERSE 14..1 LOOP
    n_per := 1 + (d % 3);
    FOR k IN 1..n_per LOOP
      i := i + 1;
      v := trucks_arr[1 + (i % array_length(trucks_arr,1))];
      c := conts_arr[1 + (i % array_length(conts_arr,1))];
      comp := comps[1 + (i % array_length(comps,1))];
      drv := drivers[1 + (i % 3)];
      base := date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'Asia/Seoul' - (d || ' days')::interval + ((7 + k * 3 + (i % 2)) || ' hours')::interval;
      dep := base;
      arr := dep + ((40 + (i * 7) % 55) || ' minutes')::interval;
      late := CASE comp WHEN 1 THEN (i % 17 = 0) WHEN 6 THEN (i % 3 = 0) WHEN 16 THEN (i % 2 = 0) ELSE false END; -- 업체별 지연 차이 (등급 A·B·C 가 섞이게)
      eta_t := CASE WHEN late THEN arr - interval '25 minutes' ELSE arr + interval '20 minutes' END;
      INSERT INTO shipments (shipment_code, container_no, origin_center_id, destination_center_id, status, started_at, completed_at)
        VALUES ('SEED-S' || lpad(i::text,4,'0'), c, 6, 1, 'AT_HUB', dep, arr) RETURNING shipment_id INTO sid;
      INSERT INTO shipment_legs (shipment_id, vehicle_no, from_center_id, to_center_id, leg_sequence, leg_type, status, departed_at, arrived_at)
        VALUES (sid, v, 6, 1, 1, 'PORT_TO_HUB', 'ARRIVED', dep, arr) RETURNING assignment_id INTO aid;
      INSERT INTO transport_orders (order_no, company_id, container_no, vehicle_no, driver_account_id, origin_terminal_id, dest_hub_id, status,
                                    eta, departed_at, arrived_at, handed_over_at, created_at, shipment_id, assignment_id)
        VALUES ('SEED-' || lpad(i::text,4,'0'), comp, c, v, drv, 13, 1, 'HANDED_OVER',
                eta_t, dep, arr, arr + ((8 + (i * 5) % 30) || ' minutes')::interval, dep - interval '2 hours', sid, aid);
      INSERT INTO hub_inbound_receipts (assignment_id, hub_id, dock_id, vehicle_no, container_no, parcel_count, status, arrived_at, unloaded_at)
        VALUES (aid, 1, docks[1 + (i % 3)], v, c, 5 + i % 9, 'COMPLETED', arr, arr + ((22 + (i * 11) % 45) || ' minutes')::interval);
    END LOOP;
  END LOOP;

  -- (2) 지금 상황 ① 하역 대기 95분 (주의) + 하역 중 140분 (긴급)
  FOR k IN 1..2 LOOP
    i := i + 1;
    v := trucks_arr[1 + (i % array_length(trucks_arr,1))]; c := conts_arr[1 + (i % array_length(conts_arr,1))];
    dep := now() - (CASE k WHEN 1 THEN 150 ELSE 200 END || ' minutes')::interval;
    arr := now() - (CASE k WHEN 1 THEN 95 ELSE 140 END || ' minutes')::interval;
    INSERT INTO shipments (shipment_code, container_no, origin_center_id, destination_center_id, status, started_at)
      VALUES ('SEED-S' || lpad(i::text,4,'0'), c, 6, 1, 'AT_HUB', dep) RETURNING shipment_id INTO sid;
    INSERT INTO shipment_legs (shipment_id, vehicle_no, from_center_id, to_center_id, leg_sequence, leg_type, status, departed_at, arrived_at)
      VALUES (sid, v, 6, 1, 1, 'PORT_TO_HUB', 'ARRIVED', dep, arr) RETURNING assignment_id INTO aid;
    INSERT INTO transport_orders (order_no, company_id, container_no, vehicle_no, driver_account_id, origin_terminal_id, dest_hub_id, status,
                                  eta, departed_at, arrived_at, created_at, shipment_id, assignment_id)
      VALUES ('SEED-' || lpad(i::text,4,'0'), comps[k], c, v, drivers[k], 13, 1, 'ARRIVED', arr + interval '10 minutes', dep, arr, dep - interval '2 hours', sid, aid);
    INSERT INTO hub_inbound_receipts (assignment_id, hub_id, dock_id, vehicle_no, container_no, parcel_count, status, arrived_at)
      VALUES (aid, 1, CASE k WHEN 1 THEN 4 ELSE 5 END, v, c, 8, CASE k WHEN 1 THEN 'ARRIVED' ELSE 'UNLOADING' END, arr);
  END LOOP;

  -- (3) 지금 상황 ② 운송 중인데 도착 예정 시각이 50분 지남 (도착 지연)
  i := i + 1;
  v := trucks_arr[1 + (i % array_length(trucks_arr,1))]; c := conts_arr[1 + (i % array_length(conts_arr,1))];
  dep := now() - interval '170 minutes';
  INSERT INTO shipments (shipment_code, container_no, origin_center_id, destination_center_id, status, started_at)
    VALUES ('SEED-S' || lpad(i::text,4,'0'), c, 6, 1, 'TO_HUB', dep) RETURNING shipment_id INTO sid;
  INSERT INTO shipment_legs (shipment_id, vehicle_no, from_center_id, to_center_id, leg_sequence, leg_type, status, departed_at)
    VALUES (sid, v, 6, 1, 1, 'PORT_TO_HUB', 'IN_TRANSIT', dep) RETURNING assignment_id INTO aid;
  INSERT INTO transport_orders (order_no, company_id, container_no, vehicle_no, driver_account_id, origin_terminal_id, dest_hub_id, status,
                                eta, departed_at, created_at, shipment_id, assignment_id)
    VALUES ('SEED-' || lpad(i::text,4,'0'), 1, c, v, 1, 13, 1, 'IN_TRANSIT', now() - interval '50 minutes', dep, dep - interval '2 hours', sid, aid);
END
$seed$;

-- (4) 점검 확정 기록 (최근 14일, 불합격 약 15%) + 방금 불합격(재점검 없음 → 경보)
INSERT INTO container_inspections (driver_account_id, container_no, items, overall_status, memo, company_id, hub_id,
                                   created_by_account_id, finalized_by_account_id, record_status, finalized_at, created_at)
SELECT 1 + (g % 3) * 0 + CASE g % 3 WHEN 0 THEN 0 WHEN 1 THEN 7 ELSE 8 END,
       (SELECT container_no FROM (SELECT container_no, row_number() OVER (ORDER BY container_no) rn FROM containers WHERE container_no NOT IN (SELECT container_no FROM container_inspections WHERE record_status = 'DRAFT' AND container_no IS NOT NULL)) x WHERE rn = g),
       jsonb_build_object('front_wall', true, 'left_side', true, 'right_side', true, 'floor', true, 'roof', true,
                          'doors', NOT (g % 7 = 0 AND (ARRAY[1,1,1,6,14,15,16,17])[1 + g % 8] IN (6,16)), 'undercarriage', true),
       CASE WHEN g % 7 = 0 AND (ARRAY[1,1,1,6,14,15,16,17])[1 + g % 8] IN (6,16) THEN 'FAIL' ELSE 'PASS' END,
       CASE WHEN g % 7 = 0 AND (ARRAY[1,1,1,6,14,15,16,17])[1 + g % 8] IN (6,16) THEN 'SEED 도어 손상 확인' ELSE 'SEED 이상 없음' END,
       (ARRAY[1,1,1,6,14,15,16,17])[1 + g % 8], 1, 5, NULL, 'DRAFT',
       NULL, now() - ((g * 7) % (14 * 24) || ' hours')::interval - interval '2 hours'
FROM generate_series(1, 36) g
WHERE NOT EXISTS (SELECT 1 FROM container_inspections WHERE memo LIKE 'SEED%');

-- 작성 중인 컨테이너는 하나만 허용되어서, 위 36건을 먼저 확정한 뒤 다음 건을 넣는다
UPDATE container_inspections SET record_status = 'FINALIZED', finalized_by_account_id = 5,
       finalized_at = created_at + interval '1 hour'
 WHERE memo LIKE 'SEED%' AND record_status = 'DRAFT';

INSERT INTO container_inspections (driver_account_id, container_no, items, overall_status, memo, company_id, hub_id,
                                   created_by_account_id, finalized_by_account_id, record_status, finalized_at, created_at)
SELECT 8, (SELECT container_no FROM (SELECT container_no, row_number() OVER (ORDER BY container_no) rn FROM containers WHERE container_no NOT IN (SELECT container_no FROM container_inspections WHERE record_status = 'DRAFT' AND container_no IS NOT NULL)) x WHERE rn = 40),
       '{"front_wall":true,"left_side":true,"right_side":false,"floor":true,"roof":true,"doors":false,"undercarriage":true}'::jsonb,
       'FAIL', 'SEED 우측 패널·도어 파손 (재점검 필요)', 1, 1, 5, NULL, 'DRAFT', NULL, now() - interval '4 hours'
WHERE NOT EXISTS (SELECT 1 FROM container_inspections WHERE memo = 'SEED 우측 패널·도어 파손 (재점검 필요)');

-- 보호 트리거 때문에 확정 기록은 직접 넣을 수 없어서, 작성 중으로 넣은 뒤 확정 처리한다 (확정 시각 = 작성 1~2시간 뒤)
UPDATE container_inspections SET record_status = 'FINALIZED', finalized_by_account_id = 5,
       finalized_at = created_at + interval '1 hour'
 WHERE memo LIKE 'SEED%' AND record_status = 'DRAFT';

-- (5) 예외·사고
INSERT INTO ops_incidents (incident_type, severity, title, detail, hub_id, status, assignee, resolution, reported_by_account_id, reported_by_user_id, created_at, updated_at, resolved_at)
SELECT * FROM (VALUES
  ('DAMAGE','HIGH','[시연] 컨테이너 우측 패널 파손','하역 중 우측 패널 변형 발견, 사진 첨부 필요',1,'OPEN',NULL,NULL,5,'hubop01', now() - interval '3 hours', now() - interval '3 hours', NULL::timestamptz),
  ('SEAL','MEDIUM','[시연] 씰 번호 불일치','도착 씰과 서류 번호 다름',1,'IN_PROGRESS','관제 담당',NULL,4,'control01', now() - interval '26 hours', now() - interval '20 hours', NULL),
  ('DELAY','LOW','[시연] 항만 반출 지연','게이트 혼잡으로 40분 지연',1,'RESOLVED','관제 담당','혼잡 해소 후 정상 반출',4,'control01', now() - interval '2 days', now() - interval '2 days' + interval '1 hour', now() - interval '2 days' + interval '1 hour'),
  ('VEHICLE','MEDIUM','[시연] 트레일러 타이어 경고','주행 중 타이어 공기압 경고',1,'RESOLVED','허브 담당','타이어 교체 완료',5,'hubop01', now() - interval '4 days', now() - interval '4 days' + interval '3 hours', now() - interval '4 days' + interval '3 hours')
) v(incident_type, severity, title, detail, hub_id, status, assignee, resolution, reported_by_account_id, reported_by_user_id, created_at, updated_at, resolved_at)
WHERE NOT EXISTS (SELECT 1 FROM ops_incidents WHERE title LIKE '[시연]%');

-- (6) 게이트 예약 (예약 시각이 지난 미확인 2건 → 경보)
INSERT INTO gate_reservations (company_id, vehicle_no, container_no, direction, planned_at, status, memo, created_by_account_id)
SELECT * FROM (VALUES
  (1, (SELECT vehicle_no FROM trucks WHERE vehicle_category='CONTAINER_TRUCK' ORDER BY vehicle_no OFFSET 0 LIMIT 1), NULL, 'IN',  now() - interval '40 minutes', 'BOOKED',     '시연 예약(예약 시각 경과)', 3),
  (1, (SELECT vehicle_no FROM trucks WHERE vehicle_category='CONTAINER_TRUCK' ORDER BY vehicle_no OFFSET 1 LIMIT 1), NULL, 'OUT', now() - interval '35 minutes', 'BOOKED',     '시연 예약(예약 시각 경과)', 3),
  (1, (SELECT vehicle_no FROM trucks WHERE vehicle_category='CONTAINER_TRUCK' ORDER BY vehicle_no OFFSET 2 LIMIT 1), NULL, 'IN',  now() + interval '2 hours',  'BOOKED',     '시연 예약', 3),
  (1, (SELECT vehicle_no FROM trucks WHERE vehicle_category='CONTAINER_TRUCK' ORDER BY vehicle_no OFFSET 3 LIMIT 1), NULL, 'OUT', now() + interval '1 day',    'BOOKED',     '시연 예약', 3),
  (1, (SELECT vehicle_no FROM trucks WHERE vehicle_category='CONTAINER_TRUCK' ORDER BY vehicle_no OFFSET 4 LIMIT 1), NULL, 'IN',  now() - interval '5 hours',  'CHECKED_IN', '시연 예약', 3),
  (1, (SELECT vehicle_no FROM trucks WHERE vehicle_category='CONTAINER_TRUCK' ORDER BY vehicle_no OFFSET 5 LIMIT 1), NULL, 'IN',  now() - interval '1 day',    'NO_SHOW',    '시연 예약', 3)
) v(company_id, vehicle_no, container_no, direction, planned_at, status, memo, created_by_account_id)
WHERE NOT EXISTS (SELECT 1 FROM gate_reservations WHERE memo LIKE '시연 예약%');
