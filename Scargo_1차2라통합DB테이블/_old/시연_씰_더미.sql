-- 26.10.20 씰 시연용 (11차 DB). 더미 오더 3건(ORD-DEMO-001~003)의 차량번호를 시연 차량으로 바꾸고, 2대에만 씰을 등록한다.
--  서울85자6013 = 정상 (SC-1001) / 경기90자8118 = 불일치 시연 (DB는 SC-1002, 도크에서 다른 번호 입력) / 경기92아5439 = 씰 미등록
-- 오더 ↔ 운송 구간(shipment_legs) 차량 일치 검사 트리거 때문에, 이 트랜잭션 안에서만 트리거를 끄고 두 테이블을 같이 바꾼다 (postgres 계정 필요).
BEGIN;
SET LOCAL session_replication_role = replica;

UPDATE shipment_legs SET vehicle_no = v.pl
FROM (VALUES ('ORD-DEMO-001','서울85자6013'), ('ORD-DEMO-002','경기90자8118'), ('ORD-DEMO-003','경기92아5439')) v(no, pl),
     transport_orders t
WHERE t.order_no = v.no AND shipment_legs.assignment_id = t.assignment_id;

UPDATE transport_orders SET vehicle_no = v.pl
FROM (VALUES ('ORD-DEMO-001','서울85자6013'), ('ORD-DEMO-002','경기90자8118'), ('ORD-DEMO-003','경기92아5439')) v(no, pl)
WHERE transport_orders.order_no = v.no;

COMMIT;

INSERT INTO container_seals (shipment_id, container_no, seal_no, attached_by_name)
SELECT t.shipment_id, t.container_no, s.seal, '시연 기사'
FROM transport_orders t JOIN (VALUES ('ORD-DEMO-001','SC-1001'), ('ORD-DEMO-002','SC-1002')) s(no, seal) ON s.no = t.order_no
WHERE NOT EXISTS (SELECT 1 FROM container_seals c WHERE c.shipment_id = t.shipment_id AND c.seal_no = s.seal);

-- 확인용
SELECT t.order_no, t.vehicle_no, l.vehicle_no AS leg_vehicle, c.seal_no
FROM transport_orders t LEFT JOIN shipment_legs l ON l.assignment_id = t.assignment_id
LEFT JOIN container_seals c ON c.shipment_id = t.shipment_id AND c.status = 'ACTIVE'
WHERE t.order_no LIKE 'ORD-DEMO-%' ORDER BY t.order_no;
