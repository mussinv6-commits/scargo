-- 26.10.27 기사 앱 '다른 기사도 도착' 확인·정리용 (PostgreSQL)
-- 원인: 오더는 driver_account_id(배차 기사)가 A 인데, 같은 차량의 전담 기사(trucks.assigned_account_id)가 B 이면
--       예전 백엔드는 A·B 둘 다 '내 오더'로 보여 줬음 → A 가 도착하면 B 화면도 '도착'.
-- 백엔드 수정(TransportOrderService.checkOwner) 후에는 배차 기사(A)에게만 보임.
-- 아래 1)로 현재 상태를 보고, 필요하면 2)·3)을 골라 실행하세요.

-- 1) 확인: 진행 중 오더의 배차 기사 vs 차량 전담 기사 (다르면 '불일치')
SELECT o.order_id, o.order_no, o.status, o.vehicle_no,
       da.user_id AS 배차기사, ta.user_id AS 차량전담기사,
       CASE WHEN o.driver_account_id IS DISTINCT FROM t.assigned_account_id THEN '불일치' ELSE '' END AS 비고
  FROM transport_orders o
  LEFT JOIN accounts da ON da.account_id = o.driver_account_id
  LEFT JOIN trucks   t  ON t.vehicle_no  = o.vehicle_no
  LEFT JOIN accounts ta ON ta.account_id = t.assigned_account_id
 WHERE o.status NOT IN ('HANDED_OVER', 'CANCELED')
 ORDER BY o.order_id;

-- 2) (선택) 불일치 오더를 '차량 전담 기사'에게 배차 (차량을 가진 기사가 운행하게 하려면)
--    전담 기사가 CONTAINER_DRIVER 여야 함 (트리거가 검사). 출발 전 오더만 바꿈
-- UPDATE transport_orders o SET driver_account_id = t.assigned_account_id, updated_at = now()
--   FROM trucks t
--  WHERE t.vehicle_no = o.vehicle_no AND t.assigned_account_id IS NOT NULL
--    AND o.driver_account_id IS DISTINCT FROM t.assigned_account_id
--    AND o.status IN ('ASSIGNED', 'LOADED', 'GATE_OUT');

-- 3) (선택, 시연 전 초기화) 시연 주행으로 도착 처리된 데모 오더 3건을 배차 상태로 되돌리기
--    인계(HANDED_OVER)까지 끝난 건 그대로 둠. 데모 오더 번호만 대상
-- BEGIN;
-- DELETE FROM trip_locations WHERE order_id IN (SELECT order_id FROM transport_orders WHERE order_no LIKE 'ORD-DEMO-%' AND status IN ('IN_TRANSIT','ARRIVED'));
-- DELETE FROM hub_inbound_receipts WHERE status = 'ARRIVED'   -- 허브 웹에서 도착 확정한 경우 남는 입고 기록 (다시 도착 확정할 수 있게)
--  AND assignment_id IN (SELECT assignment_id FROM transport_orders WHERE order_no LIKE 'ORD-DEMO-%' AND status = 'ARRIVED');
-- UPDATE shipment_legs SET status = 'ASSIGNED', departed_at = NULL, arrived_at = NULL
--  WHERE assignment_id IN (SELECT assignment_id FROM transport_orders WHERE order_no LIKE 'ORD-DEMO-%' AND status IN ('IN_TRANSIT','ARRIVED'));
-- UPDATE transport_orders SET status = 'ASSIGNED', departed_at = NULL, arrived_at = NULL, eta = NULL, updated_at = now()
--  WHERE order_no LIKE 'ORD-DEMO-%' AND status IN ('IN_TRANSIT','ARRIVED');
-- COMMIT;
