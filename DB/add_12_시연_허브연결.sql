-- 26.11.02 시연 데이터 연결: 항만→허브 화물차 5대 · 허브→물류센터 택배차 5대 · 물건(바코드) 연결
-- 여러 번 실행해도 중복되지 않는다 (이미 있으면 건너뜀). 사용: bat\db_update_1102.bat 에서 add_12_*.sql 이 함께 실행됨
BEGIN;

-- 1) 항만→허브 화물차 5대: 구간 관제 차량과 같은 번호판 (없으면 추가)
INSERT INTO trucks (vehicle_no, company_id, is_semi_trailer, trailer_no, truck_type, max_load_ton, status, vehicle_category, entry_approval)
SELECT v.no, 1, TRUE, v.tr, '견인형-세미트레일러(시연)', 27.00, 'OUTSIDE', 'CONTAINER_TRUCK', 'APPROVED'
  FROM (VALUES ('인천98아2578','시연TR-2578'),('부산91아4739','시연TR-4739')) AS v(no, tr)
 WHERE NOT EXISTS (SELECT 1 FROM trucks t WHERE t.vehicle_no = v.no);
UPDATE trucks SET vehicle_category = 'CONTAINER_TRUCK', is_semi_trailer = TRUE,
       trailer_no = COALESCE(trailer_no, '시연TR-' || right(vehicle_no, 4))
 WHERE vehicle_no IN ('인천98아2578','부산91아4739') AND vehicle_category IS DISTINCT FROM 'CONTAINER_TRUCK';

-- 2) 허브 출입 차량 등록 (화물차 5 + 택배차 5)
INSERT INTO hub_vehicles (plate_no, vehicle_kind, carrier, memo)
SELECT v.no, v.kind, 'S카고', v.memo
  FROM (VALUES ('서울85자6013','CARGO','입고 화물차'),('경기92아5439','CARGO','입고 화물차'),('경기90자8118','CARGO','입고 화물차'),
               ('인천98아2578','CARGO','입고 화물차'),('부산91아4739','CARGO','입고 화물차'),
               ('인천98바2578','COURIER','권역 출고 택배차'),('대구80자6850','COURIER','권역 출고 택배차'),('경기96사1256','COURIER','권역 출고 택배차'),
               ('대구80자7492','COURIER','권역 출고 택배차'),('부산91자5787','COURIER','권역 출고 택배차')) AS v(no, kind, memo)
 WHERE NOT EXISTS (SELECT 1 FROM hub_vehicles h WHERE h.plate_no = v.no);

-- 3) 컨테이너 · 운송 건 · 항만→허브 구간 (새 화물차 2대)
INSERT INTO containers (container_no, company_id, iso_size_type_code, container_type, is_high_cube, max_gross_kg, tare_kg, max_payload_kg, cubic_capacity_cbm, net_kg)
SELECT v.no, 16, '22G1', '일반', FALSE, 30480.0, 2200.0, 28280.0, 33.20, 28280.0
  FROM (VALUES ('SCGU4000011'),('SCGU5000012')) AS v(no)
 WHERE NOT EXISTS (SELECT 1 FROM containers c WHERE c.container_no = v.no);
INSERT INTO shipments (shipment_code, container_no, origin_center_id, status)
SELECT v.code, v.cno, 6, 'READY'
  FROM (VALUES ('SHIP-HUBDEMO-004','SCGU4000011'),('SHIP-HUBDEMO-005','SCGU5000012')) AS v(code, cno)
 WHERE NOT EXISTS (SELECT 1 FROM shipments s WHERE s.shipment_code = v.code);
INSERT INTO shipment_legs (shipment_id, vehicle_no, from_center_id, to_center_id, leg_sequence, leg_type, status)
SELECT s.shipment_id, v.no, 6, 1, 1, 'PORT_TO_HUB', 'ASSIGNED'
  FROM (VALUES ('SHIP-HUBDEMO-004','인천98아2578'),('SHIP-HUBDEMO-005','부산91아4739')) AS v(code, no)
  JOIN shipments s ON s.shipment_code = v.code
 WHERE NOT EXISTS (SELECT 1 FROM shipment_legs l WHERE l.shipment_id = s.shipment_id AND l.leg_type = 'PORT_TO_HUB')
   AND NOT EXISTS (SELECT 1 FROM shipment_legs l WHERE l.vehicle_no = v.no AND l.status IN ('ASSIGNED','IN_TRANSIT'));

-- 4) 물건(바코드 EAN-13): 운송 건마다 12개, 목적지는 인천(3) · 경기(4) · 서울(5) 물류센터에 고르게
CREATE TEMP TABLE _hub_items (scode text, seq int, barcode text, item_name text, weight_kg numeric, dest int) ON COMMIT DROP;
INSERT INTO _hub_items VALUES
('SHIP-ORD-DEMO-001',1,'8802610002014','의류',4.91,3),
('SHIP-ORD-DEMO-001',2,'8802610002021','도서',1.378,4),
('SHIP-ORD-DEMO-001',3,'8802610002038','생활용품',4.668,5),
('SHIP-ORD-DEMO-001',4,'8802610002045','전자기기',5.846,3),
('SHIP-ORD-DEMO-001',5,'8802610002052','식품',5.159,4),
('SHIP-ORD-DEMO-001',6,'8802610002069','화장품',3.044,5),
('SHIP-ORD-DEMO-001',7,'8802610002076','의류',1.546,3),
('SHIP-ORD-DEMO-001',8,'8802610002083','도서',5.251,4),
('SHIP-ORD-DEMO-001',9,'8802610002090','생활용품',2.294,5),
('SHIP-ORD-DEMO-001',10,'8802610002106','전자기기',4.575,3),
('SHIP-ORD-DEMO-001',11,'8802610002113','식품',2.085,4),
('SHIP-ORD-DEMO-001',12,'8802610002120','화장품',4.275,5),
('SHIP-ORD-DEMO-002',1,'8802610002137','도서',3.318,3),
('SHIP-ORD-DEMO-002',2,'8802610002144','생활용품',2.367,4),
('SHIP-ORD-DEMO-002',3,'8802610002151','전자기기',3.542,5),
('SHIP-ORD-DEMO-002',4,'8802610002168','식품',5.886,3),
('SHIP-ORD-DEMO-002',5,'8802610002175','화장품',5.313,4),
('SHIP-ORD-DEMO-002',6,'8802610002182','의류',5.559,5),
('SHIP-ORD-DEMO-002',7,'8802610002199','도서',2.136,3),
('SHIP-ORD-DEMO-002',8,'8802610002205','생활용품',1.289,4),
('SHIP-ORD-DEMO-002',9,'8802610002212','전자기기',6.167,5),
('SHIP-ORD-DEMO-002',10,'8802610002229','식품',5.284,3),
('SHIP-ORD-DEMO-002',11,'8802610002236','화장품',1.774,4),
('SHIP-ORD-DEMO-002',12,'8802610002243','의류',1.304,5),
('SHIP-ORD-DEMO-003',1,'8802610002250','생활용품',0.81,3),
('SHIP-ORD-DEMO-003',2,'8802610002267','전자기기',4.246,4),
('SHIP-ORD-DEMO-003',3,'8802610002274','식품',2.34,5),
('SHIP-ORD-DEMO-003',4,'8802610002281','화장품',4.364,3),
('SHIP-ORD-DEMO-003',5,'8802610002298','의류',1.813,4),
('SHIP-ORD-DEMO-003',6,'8802610002304','도서',3.63,5),
('SHIP-ORD-DEMO-003',7,'8802610002311','생활용품',3.066,3),
('SHIP-ORD-DEMO-003',8,'8802610002328','전자기기',5.725,4),
('SHIP-ORD-DEMO-003',9,'8802610002335','식품',3.989,5),
('SHIP-ORD-DEMO-003',10,'8802610002342','화장품',2.802,3),
('SHIP-ORD-DEMO-003',11,'8802610002359','의류',3.58,4),
('SHIP-ORD-DEMO-003',12,'8802610002366','도서',3.783,5),
('SHIP-HUBDEMO-004',1,'8802610002373','전자기기',0.637,3),
('SHIP-HUBDEMO-004',2,'8802610002380','식품',1.167,4),
('SHIP-HUBDEMO-004',3,'8802610002397','화장품',5.271,5),
('SHIP-HUBDEMO-004',4,'8802610002403','의류',1.519,3),
('SHIP-HUBDEMO-004',5,'8802610002410','도서',3.592,4),
('SHIP-HUBDEMO-004',6,'8802610002427','생활용품',0.794,5),
('SHIP-HUBDEMO-004',7,'8802610002434','전자기기',1.314,3),
('SHIP-HUBDEMO-004',8,'8802610002441','식품',2.334,4),
('SHIP-HUBDEMO-004',9,'8802610002458','화장품',2.685,5),
('SHIP-HUBDEMO-004',10,'8802610002465','의류',5.947,3),
('SHIP-HUBDEMO-004',11,'8802610002472','도서',5.201,4),
('SHIP-HUBDEMO-004',12,'8802610002489','생활용품',1.457,5),
('SHIP-HUBDEMO-005',1,'8802610002496','식품',0.958,3),
('SHIP-HUBDEMO-005',2,'8802610002502','화장품',4.837,4),
('SHIP-HUBDEMO-005',3,'8802610002519','의류',2.294,5),
('SHIP-HUBDEMO-005',4,'8802610002526','도서',5.92,3),
('SHIP-HUBDEMO-005',5,'8802610002533','생활용품',5.346,4),
('SHIP-HUBDEMO-005',6,'8802610002540','전자기기',2.656,5),
('SHIP-HUBDEMO-005',7,'8802610002557','식품',5.411,3),
('SHIP-HUBDEMO-005',8,'8802610002564','화장품',3.613,4),
('SHIP-HUBDEMO-005',9,'8802610002571','의류',2.272,5),
('SHIP-HUBDEMO-005',10,'8802610002588','도서',1.872,3),
('SHIP-HUBDEMO-005',11,'8802610002595','생활용품',2.56,4),
('SHIP-HUBDEMO-005',12,'8802610002601','전자기기',2.105,5);
INSERT INTO items (tracking_no, barcode, shipment_id, container_no, item_name, quantity, weight_kg, destination_center_id, status)
SELECT 'SCARGO-' || h.barcode, h.barcode, s.shipment_id, s.container_no, h.item_name, 1, h.weight_kg, h.dest, 'IN_CONTAINER'
  FROM _hub_items h JOIN shipments s ON s.shipment_code = h.scode
 WHERE NOT EXISTS (SELECT 1 FROM items i WHERE i.shipment_id = s.shipment_id)
   AND NOT EXISTS (SELECT 1 FROM items i WHERE i.barcode = h.barcode)
 ORDER BY s.shipment_id, h.seq;

-- 5) 화물차 ↔ 물건: 항만→허브 구간마다 그 운송 건의 물건을 실은 것으로 연결
INSERT INTO item_vehicle_assignments (item_id, shipment_id, assignment_id, status)
SELECT i.item_id, i.shipment_id, l.assignment_id, 'LOADED'
  FROM items i JOIN shipment_legs l ON l.shipment_id = i.shipment_id AND l.leg_type = 'PORT_TO_HUB'
 WHERE i.shipment_id IN (SELECT shipment_id FROM shipments WHERE shipment_code IN ('SHIP-ORD-DEMO-001','SHIP-ORD-DEMO-002','SHIP-ORD-DEMO-003','SHIP-HUBDEMO-004','SHIP-HUBDEMO-005'))
   AND NOT EXISTS (SELECT 1 FROM item_vehicle_assignments a WHERE a.item_id = i.item_id AND a.assignment_id = l.assignment_id);

-- 6) 이미 허브에 도착해 있는 화물차(경기90자8118)의 입고 접수 물건 수를 실제 물건 수에 맞춤
UPDATE hub_inbound_receipts r SET parcel_count = (SELECT count(*) FROM items i JOIN shipment_legs l ON l.shipment_id = i.shipment_id WHERE l.assignment_id = r.assignment_id)
 WHERE r.status = 'ARRIVED' AND r.parcel_count = 0
   AND EXISTS (SELECT 1 FROM items i JOIN shipment_legs l ON l.shipment_id = i.shipment_id WHERE l.assignment_id = r.assignment_id);

-- 7) 허브→물류센터 택배차 5대 (인천 2 · 경기 2 · 서울 1): 구간 + 담당 물건 연결
INSERT INTO shipment_legs (vehicle_no, from_center_id, to_center_id, leg_sequence, leg_type, status)
SELECT v.no, 1, v.dest, 1, 'HUB_TO_CENTER', 'ASSIGNED'
  FROM (VALUES ('인천98바2578',3),('대구80자6850',3),('경기96사1256',4),('대구80자7492',4),('부산91자5787',5)) AS v(no, dest)
 WHERE NOT EXISTS (SELECT 1 FROM shipment_legs l WHERE l.vehicle_no = v.no AND l.status IN ('ASSIGNED','IN_TRANSIT'));
-- 같은 센터로 가는 택배차가 둘이면 물건을 번갈아 나눔
INSERT INTO item_vehicle_assignments (item_id, shipment_id, assignment_id, status)
SELECT x.item_id, x.shipment_id, c.assignment_id, 'ASSIGNED'
  FROM (SELECT i.item_id, i.shipment_id, i.destination_center_id,
               row_number() OVER (PARTITION BY i.destination_center_id ORDER BY i.item_id) AS rn
          FROM items i WHERE i.shipment_id IN (SELECT shipment_id FROM shipments WHERE shipment_code IN ('SHIP-ORD-DEMO-001','SHIP-ORD-DEMO-002','SHIP-ORD-DEMO-003','SHIP-HUBDEMO-004','SHIP-HUBDEMO-005'))) x
  JOIN (SELECT l.assignment_id, l.to_center_id,
               row_number() OVER (PARTITION BY l.to_center_id ORDER BY l.assignment_id) - 1 AS k,
               count(*)     OVER (PARTITION BY l.to_center_id) AS n
          FROM shipment_legs l
         WHERE l.leg_type = 'HUB_TO_CENTER' AND l.status = 'ASSIGNED'
           AND l.vehicle_no IN ('인천98바2578','대구80자6850','경기96사1256','대구80자7492','부산91자5787')) c
    ON c.to_center_id = x.destination_center_id AND (x.rn - 1) % c.n = c.k
 WHERE NOT EXISTS (SELECT 1 FROM item_vehicle_assignments a WHERE a.item_id = x.item_id AND a.assignment_id = c.assignment_id);

-- 8) 항만 게이트 상하차 연동: 화물차 5대마다 컨테이너 번호가 붙은 상하차 기록(대기) — 게이트인 때 상하차가 시작되고, 컨테이너가 없는 택배차는 항만 게이트 목록에 나오지 않음
INSERT INTO loading_records (vehicle_no, container_no, location_id, status, shipment_id)
SELECT l.vehicle_no, s.container_no, (SELECT min(location_id) FROM loading_locations), 'PENDING', s.shipment_id
  FROM shipment_legs l JOIN shipments s ON s.shipment_id = l.shipment_id
 WHERE l.leg_type = 'PORT_TO_HUB' AND s.container_no IS NOT NULL
   AND l.vehicle_no IN ('서울85자6013','경기92아5439','경기90자8118','인천98아2578','부산91아4739')
   AND l.status IN ('ASSIGNED','IN_TRANSIT','ARRIVED')
   AND NOT EXISTS (SELECT 1 FROM loading_records r WHERE r.container_no = s.container_no AND r.status IN ('PENDING','IN_PROGRESS'))
   AND NOT EXISTS (SELECT 1 FROM loading_records r WHERE r.vehicle_no = l.vehicle_no AND r.container_no = s.container_no);

COMMIT;
