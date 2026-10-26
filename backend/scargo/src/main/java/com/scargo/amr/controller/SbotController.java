package com.scargo.amr.controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * 26.10.22 S-Bot 대시보드 · 택배 이동 · 물류센터 관제 공용 API (읽기 전용)
 *  허브 입고(parcels) → 출고(hub_dispatches) → 허브→센터 운송 구간(shipment_legs) → 통행료(toll_passages.fare_won / 옛 DB 는 toll_payments) 를
 *  권역(delivery_regions.region_code: GGI/SEL/ICN)별로 오늘 기준으로 합쳐서 돌려준다.
 *  주소: GET /api/amr/sbot/overview   (AMR 관제와 같은 /api/amr/** 로그인 규칙을 그대로 따른다)
 *  ※ 심야할인은 DB에 없으므로 tollAmount(결제 합계)만 준다. 할인 계산은 화면에서 한다.
 */
@RestController
@RequestMapping("/api/amr/sbot")
// 26.10.22 통합: 예전 AmrAuthConfig 인터셉터와 같은 범위(웹 ADMIN 세션 또는 허브 앱 출입증)를 @PreAuthorize 로 확인
//  (아이디가 sbot 으로 시작하기만 하면 통과하던 예외는 없앰. S-Bot 관리자 계정 sbot 은 ADMIN 이라 그대로 사용 가능)
@PreAuthorize("hasAnyRole('ADMIN', 'AMR_ADMIN', 'AMR_DEVICE')")
public class SbotController {

    @PersistenceContext
    private EntityManager em;

    // DB 서버가 UTC 여도 "오늘"과 시간대는 한국 시간(KST) 기준으로 센다
    private static final String TODAY = "d.departed_at >= (date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'Asia/Seoul')";
    private static final String[] CATS = {"전자제품", "의류", "식품"};

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(String sql) {
        return (List<Object[]>) em.createNativeQuery(sql).getResultList();
    }

    // 통행료를 통과 기록의 요금(fare_won)으로 두는 새 DB 인지 (없으면 옛 toll_payments 방식)
    private boolean hasFareWon() {
        Object c = em.createNativeQuery("SELECT count(*) FROM information_schema.columns WHERE table_name = 'toll_passages' AND column_name = 'fare_won'").getSingleResult();
        return c instanceof Number x && x.longValue() > 0;
    }


    // 26.10.25 시연용: since(밀리초)가 있으면 "오늘" 중에서도 그 시각 이후에 출발한 기록만 집계한다. 기존 기록은 지우지 않는다
    private static String todayCond(Long since) {
        if (since == null || since <= 0) return TODAY;
        return TODAY + " AND d.departed_at >= to_timestamp(" + (since / 1000L) + ")";
    }

    private static long n(Object o) { return o == null ? 0 : ((Number) o).longValue(); }

    private static Map<String, Object> blank() {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("trips", 0L);
        c.put("transit", 0L);
        c.put("items", 0L);
        c.put("tollAmount", 0L);
        c.put("tollDiscount", 0L);
        c.put("hr", new long[8]);
        c.put("cat", new long[4]);
        c.put("trucks", new ArrayList<Map<String, Object>>());
        return c;
    }

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Object> overview(@RequestParam(required = false) Long since) {
        final String TODAY_ = todayCond(since); // 26.10.25 시연 모드: since(밀리초) 이후 기록만 센다 (DB 는 지우지 않는다)
        Map<String, Map<String, Object>> centers = new LinkedHashMap<>();
        for (String k : new String[]{"GGI", "SEL", "ICN"}) centers.put(k, blank());

        String from = " FROM hub_dispatches d JOIN shipment_legs l ON l.assignment_id = d.assignment_id"
                + " JOIN delivery_regions r ON r.region_id = d.region_id"
                + " WHERE l.status <> 'CANCELED' AND " + TODAY_;

        // 1) 권역별 도착(ARRIVED) / 운송 중 차량 수, 도착 물품 수
        for (Object[] o : rows("SELECT r.region_code,"
                + " COUNT(*) FILTER (WHERE l.status IN ('ARRIVED', 'RECEIVED'))," // 26.10.23 센터 인수 확정(RECEIVED)도 도착
                + " COUNT(*) FILTER (WHERE l.status IN ('ASSIGNED','IN_TRANSIT')),"
                + " COALESCE(SUM(d.parcel_count), 0)" + from + " GROUP BY r.region_code")) {
            Map<String, Object> c = centers.get(String.valueOf(o[0]));
            if (c == null) continue;
            c.put("trips", n(o[1]));
            c.put("transit", n(o[2]));
            c.put("items", n(o[3]));
        }

        // 2) 시간대별(6~20시, 2시간 단위 8칸) 출고 물품 수
        for (Object[] o : rows("SELECT r.region_code,"
                + " CAST(LEAST(7, GREATEST(0, FLOOR((EXTRACT(HOUR FROM d.departed_at AT TIME ZONE 'Asia/Seoul') - 6) / 2))) AS integer), COALESCE(SUM(d.parcel_count), 0)"
                + from + " GROUP BY 1, 2")) {
            Map<String, Object> c = centers.get(String.valueOf(o[0]));
            if (c == null) continue;
            ((long[]) c.get("hr"))[(int) n(o[1])] = n(o[2]);
        }

        // 3) 물품 구성(전자제품 / 의류 / 식품 / 기타)
        for (Object[] o : rows("SELECT r.region_code, COALESCE(p.category, '기타'), COUNT(*)"
                + " FROM parcels p JOIN hub_dispatches d ON d.dispatch_id = p.dispatch_id"
                + " JOIN delivery_regions r ON r.region_id = d.region_id"
                + " WHERE " + TODAY_ + " GROUP BY 1, 2")) {
            Map<String, Object> c = centers.get(String.valueOf(o[0]));
            if (c == null) continue;
            int idx = 3;
            for (int i = 0; i < CATS.length; i++) if (CATS[i].equals(String.valueOf(o[1]))) idx = i;
            ((long[]) c.get("cat"))[idx] += n(o[2]);
        }

        // 4) 택배차 목록(상태 · 적재 건수)
        for (Object[] o : rows("SELECT r.region_code, l.vehicle_no, l.status, d.parcel_count, l.assignment_id"
                + from + " ORDER BY d.departed_at DESC LIMIT 60")) {
            Map<String, Object> c = centers.get(String.valueOf(o[0]));
            if (c == null) continue;
            List<Map<String, Object>> t = (List<Map<String, Object>>) c.get("trucks");
            if (t.size() >= 12) continue;
            boolean arrived = "ARRIVED".equals(String.valueOf(o[2])) || "RECEIVED".equals(String.valueOf(o[2])); // 26.10.23 RECEIVED 포함
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("no", o[1]);
            m.put("status", o[2]);
            m.put("state", arrived ? "입고 완료" : "이동 중");
            m.put("items", n(o[3]));
            m.put("assignmentId", n(o[4]));
            t.add(m);
        }

        // 5) 통행료 합계(운송 구간 기준): 새 DB 는 통과 기록의 요금(toll_passages.fare_won), 옛 DB 는 결제(toll_payments)
        String tollSql = hasFareWon()
                ? "SELECT r.region_code, COALESCE(SUM(p.fare_won), 0) FROM toll_passages p JOIN hub_dispatches d ON d.assignment_id = p.assignment_id"
                  + " JOIN delivery_regions r ON r.region_id = d.region_id"
                  + " WHERE p.passage_status = 'PASSED' AND p.fare_won IS NOT NULL AND " + TODAY_ + " GROUP BY 1"
                : "SELECT r.region_code, COALESCE(SUM(tp.amount), 0)"
                  + " FROM toll_payments tp JOIN hub_dispatches d ON d.assignment_id = tp.assignment_id"
                  + " JOIN delivery_regions r ON r.region_id = d.region_id"
                  + " WHERE tp.payment_status IN ('PAID','PENDING') AND " + TODAY_ + " GROUP BY 1";
        for (Object[] o : rows(tollSql)) {
            Map<String, Object> c = centers.get(String.valueOf(o[0]));
            if (c != null) c.put("tollAmount", n(o[1]));
        }

        // 5-2) 심야할인액: 통과 시각(KST)이 21~24시면 30%, 0~6시면 50%, 낮에는 0 (차량마다 따로 계산 · 일괄 반값이 아님)
        String hourSql = hasFareWon() ? "EXTRACT(HOUR FROM p.passed_at AT TIME ZONE 'Asia/Seoul')" : "EXTRACT(HOUR FROM tp.passed_at AT TIME ZONE 'Asia/Seoul')";
        String rate = "(CASE WHEN " + hourSql + " >= 21 THEN 0.3 WHEN " + hourSql + " < 6 THEN 0.5 ELSE 0 END)";
        String discSql = hasFareWon()
                ? "SELECT r.region_code, COALESCE(SUM(p.fare_won * " + rate + "), 0) FROM toll_passages p JOIN hub_dispatches d ON d.assignment_id = p.assignment_id"
                  + " JOIN delivery_regions r ON r.region_id = d.region_id"
                  + " WHERE p.passage_status = 'PASSED' AND p.fare_won IS NOT NULL AND p.passed_at IS NOT NULL AND " + TODAY_ + " GROUP BY 1"
                : "SELECT r.region_code, COALESCE(SUM(tp.amount * " + rate + "), 0)"
                  + " FROM toll_payments tp JOIN hub_dispatches d ON d.assignment_id = tp.assignment_id"
                  + " JOIN delivery_regions r ON r.region_id = d.region_id"
                  + " WHERE tp.payment_status IN ('PAID','PENDING') AND tp.passed_at IS NOT NULL AND " + TODAY_ + " GROUP BY 1";
        try {
            for (Object[] o : rows(discSql)) {
                Map<String, Object> c = centers.get(String.valueOf(o[0]));
                if (c != null) c.put("tollDiscount", o[1] == null ? 0L : Math.round(((Number) o[1]).doubleValue()));
            }
        } catch (Exception e) { /* 통과 시각 열이 없는 옛 DB 는 할인액 없이(기본 요금 그대로) 보낸다 */ }

        // 6) 허브 반입(입고): 오늘 허브로 들어온 화물차 수 · 물품 수
        Map<String, Object> hub = new LinkedHashMap<>();
        List<Object[]> hi = rows("SELECT COUNT(DISTINCT receipt_id), COUNT(*) FROM parcels WHERE inbound_at >= (date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'Asia/Seoul')");
        hub.put("trucks", hi.isEmpty() ? 0 : n(hi.get(0)[0]));
        hub.put("items", hi.isEmpty() ? 0 : n(hi.get(0)[1]));

        long total = 0;
        for (Map<String, Object> c : centers.values()) total += n(c.get("trips")) + n(c.get("transit"));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("at", System.currentTimeMillis());
        res.put("hasData", total > 0);
        res.put("centers", centers);
        // AMR 장비별 처리 현황(amr_task_logs): 최근 1시간(시간당) · 오늘 누적 건수
        List<Map<String, Object>> amrList = new ArrayList<>();
        try {
            String today = "(date_trunc('day', now() AT TIME ZONE 'Asia/Seoul') AT TIME ZONE 'Asia/Seoul')";
            for (Object[] a : rows("SELECT r.robot_code, r.state, r.robot_role,"
                    + " (SELECT count(*) FROM amr_task_logs t WHERE t.robot_code = r.robot_code AND t.finished_at >= now() - interval '1 hour'),"
                    + " (SELECT count(*) FROM amr_task_logs t WHERE t.robot_code = r.robot_code AND t.finished_at >= " + today + "),"
                    + " (SELECT count(*) FROM hub_events e WHERE e.robot_id = r.robot_id AND e.created_at >= " + today + " AND e.message LIKE '%충전소 이동%'),"
                    + " (SELECT count(*) FROM amr_maintenance mt WHERE mt.robot_id = r.robot_id AND mt.scheduled_at >= " + today + " AND mt.status <> 'CANCELED')"
                    + " FROM amr_robots r ORDER BY r.robot_code")) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("code", String.valueOf(a[0])); m.put("state", String.valueOf(a[1])); m.put("spare", "SPARE".equals(String.valueOf(a[2])));
                m.put("hour", n(a[3])); m.put("today", n(a[4])); m.put("charge", n(a[5])); m.put("fault", n(a[6]));
                amrList.add(m);
            }
        } catch (Exception e) { /* AMR 기록 테이블이 없거나 비어 있으면 건너뜀 */ }
        res.put("amr", amrList);
        res.put("hub", hub);
        res.put("speed", com.scargo.amr.service.AmrBackgroundSim.speed);
        return res;
    }

    // ────────────────────────────────────────────────────────────────
    // 쓰기: 택배 이동 화면이 택배차를 출발 · 도착시킬 때 호출 → DB 에 실제 기록이 남는다
    //  POST /api/amr/sbot/dispatch  {"region":"GGI","items":12}  → 허브 출고 + 허브→센터 운송 구간(운송 중) 생성
    //  POST /api/amr/sbot/arrive    {"assignmentId":123}          → 운송 구간 '도착' 처리
    // ────────────────────────────────────────────────────────────────
    // 테스트용 택배차(일반화물차) 30대: 서버가 처음 출발 요청을 받을 때 한 번 등록한다(이미 있으면 건너뜀) → 번호가 겹치지 않고 돌아간다
    private static volatile boolean testTrucksReady = false;

    private void ensureTestTrucks() {
        if (testTrucksReady) return;
        em.createNativeQuery("DO $$ DECLARE pre text[] DEFAULT ARRAY['서울','경기','인천','부산','대구','광주','대전','울산','충남','충북','전남','경남'];"
                + " chr text[] DEFAULT ARRAY['바','사','아','자']; v text; i int;"
                + " BEGIN FOR i IN 1..30 LOOP"
                + " v := pre[1 + (i % 12)] || (80 + (i % 20)) || chr[1 + (i % 4)] || lpad(CAST(1000 + (i * 7919) % 9000 AS text), 4, '0');"
                + " BEGIN INSERT INTO trucks (vehicle_no, company_id, is_semi_trailer, truck_type, status, vehicle_category, entry_approval)"
                + " SELECT v, (SELECT company_id FROM companies ORDER BY company_id LIMIT 1), FALSE, '택배차(테스트)', 'OUTSIDE', 'GENERAL_TRUCK', 'APPROVED'"
                + " ON CONFLICT (vehicle_no) DO NOTHING; EXCEPTION WHEN OTHERS THEN RAISE NOTICE 'test truck skipped %', SQLERRM; END;"
                + " END LOOP; END $$").executeUpdate();
        testTrucksReady = true;
    }

    // 대시보드 배속: AMR 백그라운드 시뮬레이터를 n배 빠르게 (1·2·4·8·16)
    @PostMapping("/speed")
    public Map<String, Object> speed(@RequestBody Map<String, Object> body) {
        int x = 1;
        try { x = Integer.parseInt(String.valueOf(body.get("x"))); } catch (Exception e) { /* 1배 */ }
        com.scargo.amr.service.AmrBackgroundSim.speed = Math.max(1, Math.min(16, x));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("speed", com.scargo.amr.service.AmrBackgroundSim.speed);
        return r;
    }

    @PostMapping("/dispatch")
    @Transactional
    public Map<String, Object> dispatch(@RequestBody Map<String, Object> body) {
        String region = String.valueOf(body.getOrDefault("region", ""));
        int items = body.get("items") instanceof Number num ? num.intValue() : 10;
        if (items < 1) items = 1;

        ensureTestTrucks();

        // 세 권역이 동시에 출발 요청하면 같은 차량을 고르다 '차량당 운송 중 1건' 제약에 걸려 500 이 나므로, 출발 처리는 한 번에 하나씩 직렬화한다
        em.createNativeQuery("SELECT 1 FROM (SELECT pg_advisory_xact_lock(77700123)) x").getResultList();

        List<Object[]> hub = rows("SELECT hub_id, center_id FROM hubs ORDER BY hub_id LIMIT 1");
        if (hub.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "허브가 없습니다");
        long hubId = n(hub.get(0)[0]), hubCenter = n(hub.get(0)[1]);

        List<Object[]> reg = em.createNativeQuery(
                "SELECT d.region_id, (SELECT c.center_id FROM logistics_centers c WHERE c.region_id = d.region_id"
                + " AND c.center_type = 'DISTRIBUTION_CENTER' ORDER BY c.center_id LIMIT 1),"
                + " (SELECT k.dock_id FROM hub_docks k WHERE k.hub_id = :hub AND k.dock_type = 'OUT' AND k.region_id = d.region_id ORDER BY k.dock_id LIMIT 1)"
                + " FROM delivery_regions d WHERE d.region_code = :code")
                .setParameter("hub", hubId).setParameter("code", region).getResultList();
        if (reg.isEmpty() || reg.get(0)[1] == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "알 수 없는 권역: " + region);
        long regionId = n(reg.get(0)[0]), centerId = n(reg.get(0)[1]);
        Long dockId = reg.get(0)[2] == null ? null : n(reg.get(0)[2]);

        // 화면을 닫아서 도착 처리가 안 된 오래된 운송 구간(10분 이상)은 도착으로 정리해 차량을 되돌린다
        em.createNativeQuery("UPDATE shipment_legs SET status = 'ARRIVED', arrived_at = now() WHERE leg_type = 'HUB_TO_CENTER'"
                + " AND status IN ('ASSIGNED','IN_TRANSIT') AND departed_at < now() - interval '10 minutes'").executeUpdate();

        // 1순위: 허브 출차 OCR(hub_vehicle_logs, 택배차 EXIT)에서 인식됐고 아직 출고 처리 안 된 차량 · 적재 상자 수(box_count) · 목적지(dest_hub)
        String vehicle = null;
        boolean fromOcr = false;
        Long photoLogId = null; // OCR 로 찍힌 사진(hub_vehicle_logs.log_id) → /api/amr/vehicles/{id}/photo
        List<Object[]> ocr = em.createNativeQuery(
                "SELECT h.recognized_plate, h.box_count, h.log_id FROM hub_vehicle_logs h JOIN delivery_regions r ON r.region_code = :code"
                + " WHERE h.direction = 'EXIT' AND h.vehicle_kind = 'COURIER' AND h.recognized_plate IS NOT NULL"
                + " AND h.created_at >= now() - interval '3 days'"
                + " AND (h.dest_hub = r.region_code OR h.dest_hub = r.region_name OR h.dest_hub ILIKE '%' || r.region_name || '%')"
                + " AND NOT EXISTS (SELECT 1 FROM hub_dispatches d WHERE d.delivery_vehicle_no = h.recognized_plate AND d.departed_at >= h.created_at)"
                + " AND NOT EXISTS (SELECT 1 FROM shipment_legs l WHERE l.vehicle_no = h.recognized_plate AND l.status IN ('ASSIGNED','IN_TRANSIT'))"
                + " ORDER BY h.created_at LIMIT 1").setParameter("code", region).getResultList();
        if (!ocr.isEmpty()) {
            String plate = String.valueOf(ocr.get(0)[0]);
            String pq = plate.replace("'", "''");
            // 운송 구간은 trucks 에 등록된 일반화물차만 쓸 수 있으므로, OCR 로 인식된 택배차가 없으면 등록한다 (실패해도 아래에서 걸러진다)
            em.createNativeQuery("DO $$ BEGIN INSERT INTO trucks (vehicle_no, company_id, is_semi_trailer, truck_type, status, vehicle_category, entry_approval)"
                    + " SELECT '" + pq + "', (SELECT company_id FROM companies ORDER BY company_id LIMIT 1), FALSE, '택배차', 'OUTSIDE', 'GENERAL_TRUCK', 'APPROVED'"
                    + " ON CONFLICT (vehicle_no) DO NOTHING; EXCEPTION WHEN OTHERS THEN RAISE NOTICE 'truck register skipped %', SQLERRM; END $$").executeUpdate();
            List<?> ok = em.createNativeQuery("SELECT 1 FROM trucks WHERE vehicle_no = :p AND vehicle_category = 'GENERAL_TRUCK'")
                    .setParameter("p", plate).getResultList();
            if (!ok.isEmpty()) {
                vehicle = plate;
                fromOcr = true;
                if (ocr.get(0)[2] != null) photoLogId = n(ocr.get(0)[2]);
                if (ocr.get(0)[1] != null && n(ocr.get(0)[1]) > 0) items = (int) n(ocr.get(0)[1]);
            }
        }

        // 2순위(OCR 기록이 없을 때만): 운송 중인 구간이 없는 등록 일반화물차 1대
        if (vehicle == null) {
            List<?> tr = em.createNativeQuery("SELECT t.vehicle_no FROM trucks t WHERE t.vehicle_category = 'GENERAL_TRUCK'"
                    + " AND NOT EXISTS (SELECT 1 FROM shipment_legs l WHERE l.vehicle_no = t.vehicle_no AND l.status IN ('ASSIGNED','IN_TRANSIT'))"
                    + " ORDER BY (SELECT max(d.departed_at) FROM hub_dispatches d WHERE d.delivery_vehicle_no = t.vehicle_no) NULLS FIRST, random() LIMIT 1").getResultList(); // 가장 오래 안 나간 차량부터 → 같은 차량이 겹쳐 반복되지 않는다
            if (tr.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "배정 가능한 일반화물차가 없습니다");
            vehicle = String.valueOf(tr.get(0));
        }

        Object asg = em.createNativeQuery("INSERT INTO shipment_legs (vehicle_no, from_center_id, to_center_id, leg_sequence, leg_type, status, departed_at)"
                + " VALUES (:v, :f, :t, 1, 'HUB_TO_CENTER', 'IN_TRANSIT', now()) RETURNING assignment_id")
                .setParameter("v", vehicle).setParameter("f", hubCenter).setParameter("t", centerId).getSingleResult();
        long assignmentId = n(asg);

        em.createNativeQuery("INSERT INTO hub_dispatches (assignment_id, hub_id, dock_id, region_id, delivery_vehicle_no, parcel_count, total_weight_kg, departed_at)"
                + " VALUES (:a, :h, :d, :r, :v, :n, :w, now())")
                .setParameter("a", assignmentId).setParameter("h", hubId).setParameter("d", dockId)
                .setParameter("r", regionId).setParameter("v", vehicle).setParameter("n", items).setParameter("w", items * 6.5)
                .executeUpdate();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("assignmentId", assignmentId);
        res.put("vehicleNo", vehicle);
        res.put("items", items);
        res.put("fromOcr", fromOcr);
        res.put("logId", photoLogId);
        return res;
    }

    // 택배 이동 화면에서 정한 통행료(재정 + 민자 합계): 센터에 도착하면 이 금액을 통행료 결제(PAID)로 기록한다
    private static final Map<String, Long> BASE_TOLL = Map.of("GGI", 3720L, "SEL", 4530L, "ICN", 6310L);
    private static final Map<String, String> SIDO = Map.of("GGI", "경기도", "SEL", "서울특별시", "ICN", "인천광역시");

    @PostMapping("/arrive")
    @Transactional
    public Map<String, Object> arrive(@RequestBody Map<String, Object> body) {
        Object id = body.get("assignmentId");
        if (!(id instanceof Number num)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assignmentId 가 필요합니다");
        long asg = num.longValue();
        int updated = em.createNativeQuery("UPDATE shipment_legs SET status = 'ARRIVED', arrived_at = now()"
                + " WHERE assignment_id = :a AND leg_type = 'HUB_TO_CENTER' AND status IN ('ASSIGNED','IN_TRANSIT')")
                .setParameter("a", asg).executeUpdate();

        boolean paid = false;
        if (updated > 0) {
            // 구간의 차량 · 권역 → 통과 기록(toll_passages) 1건 + 결제(toll_payments) 1건
            List<Object[]> info = rows("SELECT l.vehicle_no, r.region_code FROM shipment_legs l"
                    + " JOIN hub_dispatches d ON d.assignment_id = l.assignment_id"
                    + " JOIN delivery_regions r ON r.region_id = d.region_id WHERE l.assignment_id = " + asg);
            if (!info.isEmpty() && BASE_TOLL.containsKey(String.valueOf(info.get(0)[1]))) {
                String vehicle = String.valueOf(info.get(0)[0]);
                String code = String.valueOf(info.get(0)[1]);
                List<?> gate = em.createNativeQuery("SELECT tollgate_id FROM tollgates WHERE is_active = TRUE"
                        + " ORDER BY (CASE WHEN sido = :sido THEN 0 ELSE 1 END), tollgate_id LIMIT 1")
                        .setParameter("sido", SIDO.get(code)).getResultList();
                if (!gate.isEmpty()) {
                    long gateId = n(gate.get(0));
                    String v = vehicle.replace("'", "''");
                    // 통행료 기록이 DB 제약에 걸려도 도착 처리 자체는 유지되도록, 실패를 블록 안에서 삼킨다 (PL/pgSQL EXCEPTION)
                    String ins = hasFareWon()
                            // 새 DB: 통행료는 통과 기록 1건의 요금(fare_won)으로만 남긴다 (결제 테이블 없음)
                            ? "DO $$ BEGIN"
                              + " INSERT INTO toll_passages (tollgate_id, recognized_plate_no, vehicle_no, assignment_id, recognition_status, passage_status, detected_at, passed_at, fare_won)"
                              + " VALUES (" + gateId + ", '" + v + "', '" + v + "', " + asg + ", 'SUCCESS', 'PASSED', now(), now(), " + BASE_TOLL.get(code) + ");"
                              + " EXCEPTION WHEN OTHERS THEN RAISE NOTICE 'toll record skipped %', SQLERRM; END $$"
                            : "DO $$ DECLARE pid BIGINT; BEGIN"
                              + " INSERT INTO toll_passages (tollgate_id, recognized_plate_no, vehicle_no, assignment_id, recognition_status, passage_status, detected_at, passed_at)"
                              + " VALUES (" + gateId + ", '" + v + "', '" + v + "', " + asg + ", 'SUCCESS', 'PASSED', now(), now()) RETURNING passage_id INTO pid;"
                              + " INSERT INTO toll_payments (tollgate_id, vehicle_no, assignment_id, amount, payment_method, payment_status, passed_at, paid_at, passage_id, toll_system)"
                              + " VALUES (" + gateId + ", '" + v + "', " + asg + ", " + BASE_TOLL.get(code) + ", 'HI_PASS', 'PAID', now(), now(), pid, 'OPEN');"
                              + " EXCEPTION WHEN OTHERS THEN RAISE NOTICE 'toll record skipped %', SQLERRM; END $$";
                    // 통행료 기록이 DB 제약에 걸려도 도착 처리 자체는 유지되도록, 실패를 블록 안에서 삼킨다 (PL/pgSQL EXCEPTION)
                    em.createNativeQuery(ins).executeUpdate();
                    paid = true;
                }
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("updated", updated);
        res.put("tollPaid", paid);
        return res;
    }
}
