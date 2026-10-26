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
            SELECT order_id AS "orderId", order_no AS "orderNo", container_no AS "containerNo" FROM transport_orders
             WHERE driver_account_id = ? AND status NOT IN ('HANDED_OVER','CANCELED') ORDER BY assigned_at NULLS LAST, order_id LIMIT 1""", CurrentAccount.id());
        return l.isEmpty() ? null : l.get(0);
    }

    /** 오늘 점검 여부 (선택 화면 카드 상태) */
    @GetMapping("/today")
    public Map<String, Object> today() {
        Long me = CurrentAccount.id();
        Map<String, Object> m = new LinkedHashMap<>();
        String veh = myVehicle(); Map<String, Object> o = currentOrder();
        m.put("vehicleNo", veh); m.put("order", o);
        m.put("vehicle", latest("vehicle_checklist", "SELECT inspection_id AS \"id\", overall_status AS \"overall\", created_at AS \"at\" FROM vehicle_checklist WHERE inspector_account_id = ? AND inspection_date = CURRENT_DATE ORDER BY created_at DESC LIMIT 1", me));
        // fix_1611: 현재 오더가 없으면 오늘 저장한 최신 점검(오더 무관), 있으면 그 오더 기준
        Long oid = o == null || o.get("orderId") == null ? null : ((Number) o.get("orderId")).longValue();
        m.put("container", oid == null
            ? latest("container_inspections", "SELECT inspection_id AS \"id\", overall_status AS \"overall\", created_at AS \"at\" FROM container_inspections WHERE driver_account_id = ? AND created_at >= CURRENT_DATE ORDER BY created_at DESC LIMIT 1", me)
            : latest("container_inspections", "SELECT inspection_id AS \"id\", overall_status AS \"overall\", created_at AS \"at\" FROM container_inspections WHERE driver_account_id = ? AND order_id = ? ORDER BY created_at DESC LIMIT 1", me, oid));
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
        // 26.10.26 통합: 웹 컨테이너 점검표(share, 7-point)와 같은 표를 쓰므로 7개 항목 키도 함께 기록 → 웹 점검표 화면에서 같은 기준으로 보인다
        clean.put("front_wall", clean.get("frontWall"));
        clean.put("left_side", clean.get("sideWalls"));
        clean.put("right_side", clean.get("sideWalls"));
        clean.put("floor", clean.get("floor"));
        clean.put("roof", clean.get("roof"));
        clean.put("doors", clean.get("doors"));
        clean.put("undercarriage", clean.get("undercarriage"));
        Long id = jdbc.queryForObject("INSERT INTO container_inspections (order_id, driver_account_id, container_no, items, overall_status, memo) VALUES (?,?,?,?::jsonb,?,?) RETURNING inspection_id", Long.class,
                o == null ? null : o.get("orderId"), CurrentAccount.id(), o == null ? null : o.get("containerNo"), om.writeValueAsString(clean), all ? "PASS" : "FAIL", memo(b));
        return Map.of("id", id, "overall", all ? "PASS" : "FAIL");
    }

    private static String memo(Map<String, Object> b) { Object m = b.get("memo"); return m == null || String.valueOf(m).isBlank() ? null : String.valueOf(m).trim(); }
}
