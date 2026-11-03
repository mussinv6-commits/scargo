-- add_9_지선기사_배차_샘플.sql
-- linehaul01 이름을 지선 표기로 맞추고, 지선(HUB_TO_CENTER) 배차 샘플 2건을 넣는다.
-- 재실행 시: 계정 이름만 다시 맞추고, 샘플 unit_id 가 있으면 운행 INSERT 는 생략.

BEGIN;

UPDATE public.accounts
SET user_name = '이지선',
    user_type = 'BRANCH_DRIVER'
WHERE user_id = 'linehaul01'
  AND account_id = 7;

UPDATE public.trucks
SET assigned_account_id = 7
WHERE vehicle_no = '경기96사1256'
  AND vehicle_category = 'GENERAL_TRUCK';

DO $$
DECLARE
  v_driver bigint := 7;
  v_hub bigint := 1;          -- 곤지암허브
  v_incheon bigint := 3;      -- 인천 물류센터
  v_seoul bigint := 5;        -- 서울 물류센터
  v_truck_assigned varchar := '경기96사1256';
  v_truck_transit varchar := '경북87아6440';
  id_transit bigint;
  id_assigned bigint;
  sample_exists boolean;
BEGIN
  SELECT EXISTS (
    SELECT 1 FROM branch_units WHERE unit_id IN ('RT-IC-SAMPLE-01', 'RT-SE-SAMPLE-01')
  ) INTO sample_exists;

  IF sample_exists THEN
    RAISE NOTICE '지선 샘플(RT-*-SAMPLE-*)이 이미 있어 운행 INSERT 생략';
    RETURN;
  END IF;

  -- (A) 운행 중 샘플 (기사 앱 current 는 더 큰 id 의 ASSIGNED 우선)
  INSERT INTO public.shipment_legs (
    vehicle_no, from_center_id, to_center_id, leg_sequence, leg_type, status,
    driver_account_id, planned_units, planned_depart_at, hub_arrived_at,
    domestic_seal_no, departed_at, created_at
  ) VALUES (
    v_truck_transit, v_hub, v_seoul, 1, 'HUB_TO_CENTER', 'IN_TRANSIT',
    v_driver, 2,
    now() - interval '3 hours',
    now() - interval '2 hours 40 minutes',
    'DS-SAMPLE-INTRANSIT',
    now() - interval '2 hours',
    now()
  ) RETURNING assignment_id INTO id_transit;

  INSERT INTO public.branch_units (unit_id, assignment_id, dest_center_id, status, loaded_at)
  VALUES
    ('RT-SE-SAMPLE-01', id_transit, v_seoul, 'LOADED', now() - interval '2 hours 10 minutes'),
    ('RT-SE-SAMPLE-02', id_transit, v_seoul, 'LOADED', now() - interval '2 hours 5 minutes');

  -- (B) 배정(ASSIGNED) 샘플 — 오늘 지선 운행 카드에 표시
  INSERT INTO public.shipment_legs (
    vehicle_no, from_center_id, to_center_id, leg_sequence, leg_type, status,
    driver_account_id, planned_units, planned_depart_at, created_at
  ) VALUES (
    v_truck_assigned, v_hub, v_incheon, 1, 'HUB_TO_CENTER', 'ASSIGNED',
    v_driver, 2,
    date_trunc('day', now()) + interval '16 hours',
    now()
  ) RETURNING assignment_id INTO id_assigned;

  INSERT INTO public.branch_units (unit_id, assignment_id, dest_center_id, status)
  VALUES
    ('RT-IC-SAMPLE-01', id_assigned, v_incheon, 'SORTING'),
    ('RT-IC-SAMPLE-02', id_assigned, v_incheon, 'SORTING');

  RAISE NOTICE '지선 샘플 생성: IN_TRANSIT=#%, ASSIGNED=#%', id_transit, id_assigned;
END $$;

COMMIT;

SELECT account_id, user_id, user_name, user_type
FROM accounts WHERE user_id = 'linehaul01';

SELECT assignment_id, vehicle_no, from_center_id, to_center_id, leg_type, status,
       driver_account_id, planned_units, domestic_seal_no,
       (departed_at IS NOT NULL) AS departed
FROM shipment_legs
WHERE driver_account_id = 7 AND leg_type = 'HUB_TO_CENTER'
ORDER BY assignment_id;

SELECT unit_id, assignment_id, dest_center_id, status
FROM branch_units
WHERE unit_id LIKE 'RT-%SAMPLE%'
ORDER BY unit_id;

-- =============================================================================
-- 기사 앱 입력값 안내 (LH#40 / linehaul01)
-- 롤테이너 번호: branch_units.unit_id 에 있어야 함. 형식 regex 없음. 목적지=운행 목적지(인천=3).
--   → RT-IC-SAMPLE-01 , RT-IC-SAMPLE-02  (이미 위 INSERT)
-- 국내 운송 씰: 마스터/풀 테이블 조회 없음. 형식만 검사
--   regex = ^[A-Za-z0-9][A-Za-z0-9-]{3,19}$  (4~20자, 영문·숫자·하이픈, 저장 시 대문자)
--   예비 권장값 (DB INSERT 불필요): DS-IC-0040 , DS-77001 , KR-LH-0040
-- =============================================================================
