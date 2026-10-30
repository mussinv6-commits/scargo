package com.scargo.ops.controller;

import com.scargo.app.security.CurrentAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * fix_1543: 컨테이너 기사 '출발 전 점검' — 차량 점검표(vehicle_checklist, 웹 W-15 와 같은 표) · 컨테이너 점검표(container_inspections)
 */
@RestController
@RequestMapping("/api/app/inspections")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CONTAINER_DRIVER')")
public class AppInspectionController {
    private final JdbcTemplate jdbc;
    private final com.fasterxml.jackson.databind.ObjectMapper om;
    private final com.scargo.ops.service.OpsNotifier notifier; // 26.10.29 점검표 제출 → 웹 알림

    /** 차량 점검표 항목: 키 → vehicle_checklist 컬럼 */
    static final Map<String, String> VEHICLE = new LinkedHashMap<>();
    static {
        VEHICLE.put("light", "light_status"); VEHICLE.put("brake", "brake_status"); VEHICLE.put("airBrake", "air_brake_status");
        VEHICLE.put("tire", "tire_status"); VEHICLE.put("steering", "steering_status"); VEHICLE.put("securement", "cargo_securement_status");
        VEHICLE.put("engineOil", "engine_oil_status"); VEHICLE.put("seatbelt", "seatbelt_status");
        VEHICLE.put("extinguisher", "fire_extinguisher_status"); VEHICLE.put("triangle", "safety_triangle_status");
    }
    static final List<String> CONTAINER = List.of("numberSeal", "sealIntact", "doors", "frontWall", "sideWalls", "roof", "floor", "undercarriage", "leakOdor", "twistLock", "placard");

    private String myVehicle() {
        List<String> l = jdbc.queryForList("SELECT vehicle_no FROM trucks WHERE assigned_account_id = ? ORDER BY vehicle_no LIMIT 1", String.class, CurrentAccount.id());
        return l.isEmpty() ? null : l.get(0);
    }
    private Map<String, Object> currentOrder() {
        List<Map<String, Object>> l = jdbc.queryForList("""
            SELECT order_id AS "orderId", order_no AS "orderNo", container_no AS "containerNo", assigned_at AS "assignedAt" FROM transport_orders
             WHERE driver_account_id = ? AND status NOT IN ('HANDED_OVER','CANCELED')
             ORDER BY CASE status WHEN 'ARRIVED' THEN 0 WHEN 'IN_TRANSIT' THEN 1 WHEN 'GATE_OUT' THEN 2 WHEN 'LOADED' THEN 3 WHEN 'ASSIGNED' THEN 4 ELSE 5 END,
                      assigned_at NULLS LAST, order_id LIMIT 1""", CurrentAccount.id()); // 26.10.27: 앱 홈·내비(TransportOrderService.activeOrders)와 같은 순서
        return l.isEmpty() ? null : l.get(0);
    }

    /** 오늘 점검 여부 (선택 화면 카드 상태)
     *  26.10.29 완료 판단은 서버가 한다 → vehicleDone · containerDone · ready 를 내려 주고, 앱은 이 값만 본다
     *   - 차량: 오늘(DB 날짜) 내가 '지금 배정된 내 차량'으로 저장한 차량 점검표가 있으면 완료
     *   - 컨테이너: 지금 진행 중인 오더가 있고, 그 오더로 저장한(취소 제외) 컨테이너 점검표가 배차 시각 이후에 있으면 완료
     *   vehicle · container 에는 완료로 인정된 기록만 넣는다 (예전 앱도 그대로 맞게 보이도록) */
    @GetMapping("/today")
    public Map<String, Object> today() {
        Long me = CurrentAccount.id();
        Map<String, Object> m = new LinkedHashMap<>();
        String veh = myVehicle(); Map<String, Object> o = currentOrder();
        m.put("vehicleNo", veh); m.put("order", o);
        Map<String, Object> v = veh == null ? null : latest("vehicle_checklist", "SELECT inspection_id AS \"id\", overall_status AS \"overall\", created_at AS \"at\", vehicle_no AS \"vehicleNo\" FROM vehicle_checklist WHERE inspector_account_id = ? AND vehicle_no = ? AND inspection_date = CURRENT_DATE ORDER BY created_at DESC LIMIT 1", me, veh);
        Long oid = o == null || o.get("orderId") == null ? null : ((Number) o.get("orderId")).longValue();
        Map<String, Object> c = oid == null ? null : latest("container_inspections", "SELECT ci.inspection_id AS \"id\", ci.overall_status AS \"overall\", ci.created_at AS \"at\", ci.order_id AS \"orderId\", ci.container_no AS \"containerNo\" FROM container_inspections ci "
                + "JOIN transport_orders t ON t.order_id = ci.order_id WHERE ci.driver_account_id = ? AND ci.order_id = ? AND COALESCE(ci.record_status, '') <> 'CANCELLED' "
                + "AND (t.assigned_at IS NULL OR ci.created_at >= t.assigned_at) ORDER BY ci.created_at DESC LIMIT 1", me, oid);
        m.put("vehicle", v);
        m.put("container", c);
        m.put("vehicleDone", v != null);
        m.put("containerDone", c != null);
        m.put("ready", v != null && c != null);
        return m;
    }

    /** fix_1611: 조회 실패(테이블 미생성 등)는 '미점검'으로 응답하고 로그만 남긴다 */
    private Map<String, Object> latest(String table, String sql, Object... args) {
        try { return jdbc.queryForList(sql, args).stream().findFirst().orElse(null); }
        catch (org.springframework.dao.DataAccessException ex) {
            org.slf4j.LoggerFactory.getLogger(AppInspectionController.class).warn("점검 상태 조회 실패 ({}): {} — database/16_컨테이너점검표_container_inspections.sql 실행 여부 확인", table, ex.getMostSpecificCause().getMessage());
            return null;
        }
    }

    /** 차량 점검표 저장 → vehicle_checklist (웹 차량 점검표 메뉴에 표시) */
    @PostMapping("/vehicle")
    @Transactional
    public Map<String, Object> saveVehicle(@RequestBody Map<String, Object> b) {
        String veh = myVehicle();
        if (veh == null) throw new IllegalStateException("배정된 차량이 없어 차량 점검표를 저장할 수 없습니다.");
        Map<?, ?> items = b.get("items") instanceof Map<?, ?> x ? x : Map.of();
        List<String> cols = new ArrayList<>(); List<Object> vals = new ArrayList<>();
        boolean all = true;
        for (var e : VEHICLE.entrySet()) { boolean ok = Boolean.TRUE.equals(items.get(e.getKey())); all &= ok; cols.add(e.getValue()); vals.add(ok ? "PASS" : "FAIL"); }
        cols.add("overall_status"); vals.add(all ? "PASS" : "FAIL");
        cols.add("memo"); vals.add(memo(b));
        cols.add("vehicle_no"); vals.add(veh); cols.add("inspector_account_id"); vals.add(CurrentAccount.id());
        String sql = "INSERT INTO vehicle_checklist (" + String.join(",", cols) + ") VALUES (" + String.join(",", Collections.nCopies(cols.size(), "?")) + ") RETURNING inspection_id";
        Long id = jdbc.queryForObject(sql, Long.class, vals.toArray());
        long bad = vals.stream().limit(VEHICLE.size()).filter("FAIL"::equals).count();
        notifySaved("차량 점검표", driverName() + " 기사 · " + veh + " · " + (all ? "종합 양호" : "종합 불량 (불량 " + bad + "개 항목)"), all, id, veh);
        return Map.of("id", id, "overall", all ? "PASS" : "FAIL", "vehicleNo", veh);
    }

    /** 컨테이너 점검표 저장 → container_inspections */
    @PostMapping("/container")
    @Transactional
    public Map<String, Object> saveContainer(@RequestBody Map<String, Object> b) throws Exception {
        Map<String, Object> o = currentOrder();
        Map<?, ?> items = b.get("items") instanceof Map<?, ?> x ? x : Map.of();
        Map<String, Boolean> clean = new LinkedHashMap<>(); boolean all = true;
        for (String k : CONTAINER) { boolean ok = Boolean.TRUE.equals(items.get(k)); clean.put(k, ok); all &= ok; }
        // fix_1027: DB chk_ci_items 는 웹 7개 항목 키(boolean)만 허용 → items 에는 7개 키만 저장
        //  (앱 전용 항목은 판정(overall_status)에 반영하고, 메모에 글로 남긴다)
        Map<String, Boolean> web = new LinkedHashMap<>();
        web.put("front_wall", clean.get("frontWall"));
        web.put("left_side", clean.get("sideWalls"));
        web.put("right_side", clean.get("sideWalls"));
        web.put("floor", clean.get("floor"));
        web.put("roof", clean.get("roof"));
        web.put("doors", clean.get("doors"));
        web.put("undercarriage", clean.get("undercarriage"));
        Map<String, String> label = Map.of("numberSeal", "번호·씰 확인", "sealIntact", "씰 이상 없음", "leakOdor", "누수·악취 없음", "twistLock", "트위스트락 체결", "placard", "위험물 표지");
        List<String> extra = new ArrayList<>();
        for (String k : List.of("numberSeal", "sealIntact", "leakOdor", "twistLock", "placard")) extra.add(label.get(k) + " " + (clean.get(k) ? "O" : "X"));
        String m = memo(b);
        String fullMemo = "[앱 추가항목] " + String.join(", ", extra) + (m == null ? "" : "\n" + m);
        Object cno = o == null ? null : o.get("containerNo");
        // fix_1027: 같은 컨테이너의 작성 중(DRAFT) 점검은 1건만 허용(uk_ci_container_draft) → 있으면 그 기록을 고쳐 저장(다시 저장 가능)
        List<Long> draft = cno == null ? List.of() : jdbc.queryForList(
                "SELECT inspection_id FROM container_inspections WHERE container_no = ? AND record_status = 'DRAFT' ORDER BY inspection_id DESC LIMIT 1", Long.class, cno);
        Long id;
        if (!draft.isEmpty()) {
            id = draft.get(0);
            jdbc.update("UPDATE container_inspections SET order_id = ?, driver_account_id = ?, items = ?::jsonb, overall_status = ?, memo = ?, created_at = now() WHERE inspection_id = ?",
                    o.get("orderId"), CurrentAccount.id(), om.writeValueAsString(web), all ? "PASS" : "FAIL", fullMemo, id);
        } else {
            id = jdbc.queryForObject("INSERT INTO container_inspections (order_id, driver_account_id, container_no, items, overall_status, memo) VALUES (?,?,?,?::jsonb,?,?) RETURNING inspection_id", Long.class,
                    o == null ? null : o.get("orderId"), CurrentAccount.id(), cno, om.writeValueAsString(web), all ? "PASS" : "FAIL", fullMemo);
        }
        long bad = clean.values().stream().filter(x -> !x).count();
        notifySaved("컨테이너 점검표", driverName() + " 기사 · " + (cno == null ? "컨테이너 미지정" : cno) + (o == null || o.get("orderNo") == null ? "" : " · 오더 " + o.get("orderNo"))
                + " · " + (all ? "종합 양호" : "종합 불량 (불량 " + bad + "개 항목)"), all, id, myVehicle());
        return Map.of("id", id, "overall", all ? "PASS" : "FAIL");
    }

    /** 26.10.29 앱에서 점검표를 저장하면 웹 알림 (총관리자 · 통합 관제사 + 그 차량의 운송사). 알림을 못 넣어도 점검표 저장은 그대로 */
    private void notifySaved(String kind, String msg, boolean pass, Long id, String vehicleNo) {
        String title = kind + " 제출" + (pass ? "" : " · 불량 있음");
        try {
            notifier.toOps(null, com.scargo.Enum.NotificationType.SYSTEM, title, msg, id);
            notifier.toCompany(notifier.companyOfVehicle(vehicleNo), com.scargo.Enum.NotificationType.SYSTEM, title, msg, id);
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(AppInspectionController.class).warn("점검표 제출 알림 실패: {}", ex.getMessage());
        }
    }
    private String driverName() {
        List<String> l = jdbc.queryForList("SELECT user_name FROM accounts WHERE account_id = ?", String.class, CurrentAccount.id());
        return l.isEmpty() || l.get(0) == null ? "기사" : l.get(0);
    }

    private static String memo(Map<String, Object> b) { Object m = b.get("memo"); return m == null || String.valueOf(m).isBlank() ? null : String.valueOf(m).trim(); }
}
