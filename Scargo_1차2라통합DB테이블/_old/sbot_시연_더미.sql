-- 26.10.22 S-Bot 대시보드 시연용 더미: 오늘 날짜의 허브→물류센터 운송(shipment_legs HUB_TO_CENTER) + 허브 출고(hub_dispatches)
--  · 권역별(경기 8 · 서울 6 · 인천 5)건, 마지막 1건은 '운송 중', 나머지는 '도착'
--  · 일반화물차(GENERAL_TRUCK)를 돌려 쓴다. 오늘 출고 기록이 이미 있으면 아무것도 하지 않는다 (여러 번 실행해도 안전)
--  · 통행료(toll_payments)·물품 구성(parcels)은 트리거 제약이 많아 넣지 않는다 → 화면은 기본 요금표·추정 비율로 채운다
DO $$
DECLARE
  v_hub_id   BIGINT;
  v_hub_cen  BIGINT;
  trucks     TEXT[];
  r          RECORD;
  i          INT;
  n          INT;
  v_tr       TEXT;
  v_cen      BIGINT;
  v_dock     BIGINT;
  v_asg      BIGINT;
  v_stat     TEXT;
  v_dep      TIMESTAMPTZ;
  k          INT := 0;
BEGIN
  IF EXISTS (SELECT 1 FROM hub_dispatches WHERE departed_at >= date_trunc('day', now())) THEN
    RAISE NOTICE '오늘 출고 기록이 이미 있어 건너뜁니다';
    RETURN;
  END IF;

  SELECT h.hub_id, h.center_id INTO v_hub_id, v_hub_cen FROM hubs h ORDER BY h.hub_id LIMIT 1;
  SELECT array_agg(vehicle_no ORDER BY vehicle_no) INTO trucks FROM trucks WHERE vehicle_category = 'GENERAL_TRUCK';
  IF v_hub_id IS NULL OR trucks IS NULL THEN RAISE EXCEPTION '허브 또는 일반화물차가 없습니다'; END IF;

  FOR r IN SELECT * FROM (VALUES ('GGI', 8), ('SEL', 6), ('ICN', 5)) AS t(code, cnt) LOOP
    SELECT c.center_id INTO v_cen FROM logistics_centers c JOIN delivery_regions d ON d.region_id = c.region_id
     WHERE d.region_code = r.code AND c.center_type = 'DISTRIBUTION_CENTER' LIMIT 1;
    SELECT dk.dock_id INTO v_dock FROM hub_docks dk JOIN delivery_regions d ON d.region_id = dk.region_id
     WHERE dk.hub_id = v_hub_id AND dk.dock_type = 'OUT' AND d.region_code = r.code LIMIT 1;

    FOR i IN 1..r.cnt LOOP
      k := k + 1;
      v_tr   := trucks[1 + (k - 1) % array_length(trucks, 1)];
      v_stat := CASE WHEN i = r.cnt THEN 'IN_TRANSIT' ELSE 'ARRIVED' END;
      v_dep  := date_trunc('day', now()) + make_interval(hours => 6 + ((i * 13 + k) % 14), mins => (i * 7) % 60);
      IF v_dep > now() THEN v_dep := now() - make_interval(mins => 5 * (r.cnt - i + 1)); END IF;
      IF v_dep < date_trunc('day', now()) THEN v_dep := date_trunc('day', now()) + interval '1 minute'; END IF;
      n := 6 + ((i * 5 + k * 3) % 15);

      -- 운송 중 구간은 차량당 1건만 허용(uk_legs_active_vehicle) → 운송 중 건은 전용 차량으로
      IF v_stat = 'IN_TRANSIT' THEN
        v_tr := trucks[1 + (array_length(trucks, 1) - 1 - (k % array_length(trucks, 1)))];
        IF EXISTS (SELECT 1 FROM shipment_legs WHERE vehicle_no = v_tr AND status IN ('ASSIGNED','IN_TRANSIT')) THEN
          v_stat := 'ARRIVED';
        END IF;
      END IF;

      INSERT INTO shipment_legs (vehicle_no, from_center_id, to_center_id, leg_sequence, leg_type, status, departed_at, arrived_at)
      VALUES (v_tr, v_hub_cen, v_cen, 1, 'HUB_TO_CENTER', v_stat, v_dep,
              CASE WHEN v_stat = 'ARRIVED' THEN v_dep + interval '55 minutes' END)
      RETURNING assignment_id INTO v_asg;

      INSERT INTO hub_dispatches (assignment_id, hub_id, dock_id, region_id, delivery_vehicle_no, parcel_count, total_weight_kg, departed_at)
      SELECT v_asg, v_hub_id, v_dock, d.region_id, v_tr, n, n * 6.5, v_dep FROM delivery_regions d WHERE d.region_code = r.code;
    END LOOP;
  END LOOP;
  RAISE NOTICE '시연 더미 % 건 입력 완료', k;
END $$;

-- 확인
SELECT r.region_code, l.status, count(*) AS trips, sum(d.parcel_count) AS items
  FROM hub_dispatches d JOIN shipment_legs l ON l.assignment_id = d.assignment_id
  JOIN delivery_regions r ON r.region_id = d.region_id
 WHERE d.departed_at >= date_trunc('day', now()) GROUP BY 1, 2 ORDER BY 1, 2;
