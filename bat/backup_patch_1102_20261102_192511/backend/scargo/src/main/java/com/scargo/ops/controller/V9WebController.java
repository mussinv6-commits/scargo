package com.scargo.ops.controller;

import com.scargo.app.security.CurrentAccount;
import com.scargo.entity.Account;
import com.scargo.ops.CarrierLegacy;
import com.scargo.ops.service.HubOpsService;
import com.scargo.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * 26.10.21 v9: 웹 관제 화면(기획 web.html) 실데이터 API. 권한은 화면 권한표와 같게 맞춘다.
 *  W-02 ADMIN·CONTROL·HUB_OP·CENTER_OP·운송사 | W-03 ADMIN·CONTROL | W-04 ADMIN·CONTROL·HUB_OP | W-05 ADMIN·CONTROL·CENTER_OP
 *  W-06 ADMIN·CONTROL·HUB_OP | W-07 ADMIN·CONTROL·운송사(등록은 운송사) | W-08 ADMIN·CONTROL·HUB_OP·운송사(배차는 운송사)
 *  W-09 전부 | W-10 ADMIN·운송사 | W-11 ADMIN | W-12·W-13 ADMIN·CONTROL | W-14·W-15 ADMIN·CONTROL·운송사 | W-16 ADMIN
 *  (운송사 = 승인 운송사 담당자 ROLE_CARRIER)
 */
@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class V9WebController {
    private final JdbcTemplate jdbc;
    private final AccountRepository accounts;
    private final HubOpsService hub;
    private final org.springframework.security.crypto.password.PasswordEncoder encoder;
    private final com.scargo.ops.service.ReturnDueService returnDue;
    private final com.scargo.ops.service.BranchService branch; // fix_1300: 지선 도착 봉인 확인

    private static final String W02 = "hasAnyRole('ADMIN','CONTROL','HUB_OP','CENTER_OP','CARRIER')";
    private static final String W03 = "hasAnyRole('ADMIN','CONTROL')";

    private Account me() { return accounts.findById(CurrentAccount.id()).orElseThrow(); }
    /** 운송사 담당자면 회사 id, 아니면 null(전체) */
    private Long carrierCompany(Account a) { return CarrierLegacy.isApprovedCarrierContact(a) ? a.getCompanyId() : null; }
    private int setting(String key, int def) {
        List<Integer> v = jdbc.queryForList("SELECT setting_value FROM ops_settings WHERE setting_key = ?", Integer.class, key);
        return v.isEmpty() ? def : v.get(0);
    }
    private static Long num(Object o) { if (o == null || String.valueOf(o).isBlank()) return null; return o instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(o)); }
    private static String str(Object o) { return o == null ? null : String.valueOf(o).trim(); }

    // ------------------------------------------------------------ W-02 통합 대시보드
    @GetMapping("/dashboard")
    @PreAuthorize(W02)
    public Map<String, Object> dashboard() {
        Account a = me(); Long co = carrierCompany(a);
        Map<String, Object> m = new LinkedHashMap<>();
        String coOrder = co == null ? "" : " AND o.company_id = " + co;
        String coLeg = co == null ? "" : " AND l.vehicle_no IN (SELECT vehicle_no FROM trucks WHERE company_id = " + co + ")";
        // v13: HUB_OP 는 소속 허브, CENTER_OP 는 소속 센터 범위만 (거점 id 는 서버 값이라 SQL 에 직접 넣어도 안전)
        Long scope = (a.getUserType() == Account.UserType.HUB_OP || a.getUserType() == Account.UserType.CENTER_OP) ? a.getScopeCenterId() : null;
        boolean hubOp = a.getUserType() == Account.UserType.HUB_OP;
        if (scope != null && hubOp) {
            coOrder += " AND o.dest_hub_id IN (SELECT hub_id FROM hubs WHERE center_id = " + scope + ")";
            coLeg += " AND l.from_center_id = " + scope;
        } else if (scope != null) {
            coOrder += " AND o.shipment_id IN (SELECT shipment_id FROM shipment_legs WHERE leg_type = 'HUB_TO_CENTER' AND to_center_id = " + scope + ")";
            coLeg += " AND l.to_center_id = " + scope;
        }
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("orders", jdbc.queryForObject("SELECT count(*) FROM transport_orders o WHERE o.status NOT IN ('HANDED_OVER','CANCELED')" + coOrder, Long.class));
        k.put("trips", jdbc.queryForObject("SELECT count(*) FROM shipment_legs l WHERE l.leg_type='HUB_TO_CENTER' AND l.received_at IS NULL AND l.status <> 'CANCELED'" + coLeg, Long.class));
        List<Map<String, Object>> ex = exceptionRows(a);
        long open = ex.stream().filter(e -> !"RESOLVED".equals(e.get("status"))).count();
        long crit = ex.stream().filter(e -> !"RESOLVED".equals(e.get("status")) && "CRITICAL".equals(e.get("severity"))).count();
        k.put("open", open); k.put("crit", crit); k.put("warn", open - crit);
        k.put("returnDueSoon", jdbc.queryForObject("SELECT count(*) FROM transport_orders o WHERE o.return_due_at IS NOT NULL AND o.empty_returned_at IS NULL AND o.return_due_at <= now() + make_interval(hours => ?)" + coOrder, Long.class, setting("RETURN_DUE_SOON_HOURS", 24)));
        m.put("kpi", k);
        m.put("sites", scope == null ? sites() : sites().stream().filter(x -> scope.equals(num(x.get("centerId")))).toList());
        m.put("warnDelayMin", setting("WARN_DELAY_MIN", 30));
        m.put("recent", ex.stream().limit(4).toList());
        // 오늘 흐름: 가장 최근 컨테이너(shipment) 기준
        List<Map<String, Object>> s = jdbc.queryForList("SELECT s.shipment_id, s.container_no FROM shipments s WHERE EXISTS (SELECT 1 FROM items i WHERE i.shipment_id = s.shipment_id)"
                + (co == null ? "" : " AND s.shipment_id IN (SELECT shipment_id FROM transport_orders WHERE company_id = " + co + ")") + " ORDER BY s.shipment_id DESC LIMIT 1");
        if (!s.isEmpty()) {
            Long sid = num(s.get(0).get("shipment_id"));
            Map<String, Object> f = new LinkedHashMap<>(jdbc.queryForMap("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE i.status <> 'IN_CONTAINER') AS unloaded,
                       (SELECT count(*) FROM parcels p JOIN items x ON x.item_id = p.item_id WHERE x.shipment_id = ?) AS scanned,
                       (SELECT count(*) FROM parcels p JOIN items x ON x.item_id = p.item_id WHERE x.shipment_id = ? AND p.unit_id IS NOT NULL) AS loaded
                  FROM items i WHERE i.shipment_id = ?""", sid, sid, sid));
            f.put("containerNo", s.get(0).get("container_no"));
            f.putAll(jdbc.queryForMap("SELECT count(*) AS trips, count(*) FILTER (WHERE departed_at IS NOT NULL) AS departed FROM shipment_legs WHERE leg_type='HUB_TO_CENTER' AND created_at::date = current_date"));
            m.put("flow", f);
        }
        return m;
    }

    /** 거점 현황: 허브·지역센터별 미해결 예외 + 최대 지연(분, 도착 예정 시각 경과) */
    private List<Map<String, Object>> sites() {
        return jdbc.queryForList("""
            SELECT c.center_id AS "centerId", c.center_name AS "name", CASE WHEN c.center_type = 'HUB' THEN 'H' ELSE 'C' END AS "t",
                   (SELECT count(*) FROM exceptions e WHERE e.center_id = c.center_id AND e.status <> 'RESOLVED' AND e.severity = 'CRITICAL') AS "crit",
                   (SELECT count(*) FROM exceptions e WHERE e.center_id = c.center_id AND e.status <> 'RESOLVED' AND e.severity <> 'CRITICAL') AS "warn",
                   COALESCE((SELECT max(GREATEST(0, EXTRACT(EPOCH FROM (now() - o.eta)) / 60))::int FROM transport_orders o JOIN hubs h ON h.hub_id = o.dest_hub_id
                              WHERE h.center_id = c.center_id AND o.status IN ('GATE_OUT','IN_TRANSIT') AND o.eta IS NOT NULL), 0) AS "maxDelay"
              FROM logistics_centers c WHERE c.center_type IN ('HUB','DISTRIBUTION_CENTER') AND c.is_active IS NOT FALSE ORDER BY CASE WHEN c.center_type='HUB' THEN 0 ELSE 1 END, c.center_id""");
    }

    // ------------------------------------------------------------ W-09 예외 알림 센터 (이름·거점 포함, 권한별 범위)
    @GetMapping("/exceptions/view")
    @PreAuthorize(W02)
    public List<Map<String, Object>> exceptionsView() { return exceptionRows(me()); }

    private List<Map<String, Object>> exceptionRows(Account a) {
        String where = "";
        Long co = carrierCompany(a);
        if (co != null) where = " WHERE (e.assignment_id IN (SELECT l.assignment_id FROM shipment_legs l JOIN trucks t ON t.vehicle_no = l.vehicle_no WHERE t.company_id = " + co + ")"
                // fix_1630: + 같은 회사 컨테이너 오더의 씰·오더 예외 (seal_events.order_id / transport_orders)
                + " OR e.exception_id IN (SELECT x.exception_id FROM exceptions x LEFT JOIN seal_events v ON x.source_table = 'seal_events' AND x.source_key = v.event_id::text"
                + " JOIN transport_orders o ON o.order_id = CASE WHEN x.source_table = 'transport_orders' THEN x.source_key::bigint ELSE v.order_id END"
                + " WHERE x.source_table IN ('seal_events','transport_orders') AND o.company_id = " + co + "))";
        else if (a.getUserType() == Account.UserType.CENTER_OP || a.getUserType() == Account.UserType.HUB_OP)
            where = " WHERE (e.center_id = " + a.getScopeCenterId() + " OR e.center_id IS NULL)";
        return jdbc.queryForList("""
            SELECT e.exception_id AS "exceptionId", e.exception_type AS "type", e.severity, e.status, e.message, e.source_table AS "sourceTable",
                   e.center_id AS "centerId", c.center_name AS "centerName", e.assignee_account_id AS "assigneeId", ac.user_name AS "assignee",
                   ac.user_type AS "assigneeRole", ah.hub_name AS "assigneeHub", acn.center_name AS "assigneeCenter",
                   e.occurred_at AS "occurredAt", e.resolution_note AS "note"
              FROM exceptions e LEFT JOIN logistics_centers c ON c.center_id = e.center_id LEFT JOIN accounts ac ON ac.account_id = e.assignee_account_id
              LEFT JOIN hubs ah ON ah.hub_id = ac.hub_id LEFT JOIN logistics_centers acn ON acn.center_id = ac.center_id""" + where
                + " ORDER BY CASE WHEN e.status = 'RESOLVED' THEN 1 ELSE 0 END, e.occurred_at DESC");
    }

    // ------------------------------------------------------------ W-03 네트워크 관제
    @GetMapping("/network")
    @PreAuthorize(W03)
    public Map<String, Object> network() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nodes", jdbc.queryForList("""
            SELECT c.center_id AS "centerId", c.center_name AS "name", c.center_type AS "type",
                   CASE WHEN EXISTS (SELECT 1 FROM exceptions e WHERE e.center_id = c.center_id AND e.status <> 'RESOLVED' AND e.severity='CRITICAL') THEN 'crit'
                        WHEN EXISTS (SELECT 1 FROM exceptions e WHERE e.center_id = c.center_id AND e.status <> 'RESOLVED') THEN 'warn' ELSE 'ok' END AS "level"
              FROM logistics_centers c WHERE c.center_type IN ('PORT','HUB','DISTRIBUTION_CENTER') ORDER BY c.center_id"""));
        m.put("vehicles", jdbc.queryForList("""
            SELECT o.vehicle_no AS "vehicleNo", '운송오더' AS "kind", t.terminal_name || ' → ' || h.hub_name AS "segment", o.status,
                   o.eta, GREATEST(0, COALESCE(EXTRACT(EPOCH FROM (now() - o.eta)) / 60, 0))::int AS "delayMin"
              FROM transport_orders o LEFT JOIN terminals t ON t.terminal_id = o.origin_terminal_id LEFT JOIN hubs h ON h.hub_id = o.dest_hub_id
             WHERE o.status IN ('ASSIGNED','LOADED','GATE_OUT','IN_TRANSIT') AND o.vehicle_no IS NOT NULL
            UNION ALL
            SELECT l.vehicle_no, '지선 운행 LH#' || l.assignment_id, f.center_name || ' → ' || c.center_name, l.status, NULL, 0
              FROM shipment_legs l JOIN logistics_centers f ON f.center_id = l.from_center_id JOIN logistics_centers c ON c.center_id = l.to_center_id
             WHERE l.leg_type = 'HUB_TO_CENTER' AND l.received_at IS NULL AND l.status <> 'CANCELED'
            UNION ALL
            SELECT o.vehicle_no, '공컨 반납', h.hub_name || ' → ' || COALESCE(o.return_cy, '반납 CY'), o.return_status, o.return_due_at, 0
              FROM transport_orders o LEFT JOIN hubs h ON h.hub_id = o.dest_hub_id WHERE o.return_status IN ('RETURNING_EMPTY','RETURN_ARRIVED')"""));
        return m;
    }

    // ------------------------------------------------------------ W-04 허브 관제 (5개 영역)
    @GetMapping("/hubs/{id}/board")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','HUB_OP')")
    public Map<String, Object> hubBoard(@PathVariable("id") Long id) {
        Account a = me();
        if (a.getUserType() == Account.UserType.HUB_OP && !Objects.equals(a.getHubId(), id)) throw new AccessDeniedException("소속 허브만 볼 수 있습니다.");
        Long center = jdbc.queryForObject("SELECT center_id FROM hubs WHERE hub_id = ?", Long.class, id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inbound", jdbc.queryForList("""
            SELECT o.order_no AS "orderNo", o.container_no AS "containerNo", o.eta, o.arrived_at AS "arrivedAt", o.status,
                   GREATEST(0, COALESCE(EXTRACT(EPOCH FROM (now() - o.eta)) / 60, 0))::int AS "delayMin"
              FROM transport_orders o WHERE o.dest_hub_id = ? AND o.status NOT IN ('HANDED_OVER','CANCELED') ORDER BY o.eta NULLS LAST, o.order_id""", id));
        m.put("docks", jdbc.queryForList("""
            SELECT d.dock_id AS "dockId", d.dock_code AS "no", d.dock_type AS "kind", d.status,
                   (SELECT 'LH#' || hd.assignment_id FROM hub_dispatches hd WHERE hd.dock_id = d.dock_id ORDER BY hd.dispatch_id DESC LIMIT 1) AS "trip"
              FROM hub_docks d WHERE d.hub_id = ? ORDER BY d.dock_type, d.dock_code""", id));
        m.put("seals", jdbc.queryForList("SELECT seal_id AS \"sealId\", container_no AS \"containerNo\", seal_no AS \"sealNo\", open_hold AS \"openHold\", status FROM container_seals WHERE status = 'ACTIVE' ORDER BY open_hold DESC, seal_id DESC"));
        Map<String, Object> work = new LinkedHashMap<>(jdbc.queryForMap("""
            SELECT (SELECT count(*) FROM items i JOIN transport_orders o ON o.shipment_id = i.shipment_id WHERE o.dest_hub_id = ? AND o.status IN ('ARRIVED','HANDED_OVER')) AS "boxes",
                   (SELECT count(*) FROM items i JOIN transport_orders o ON o.shipment_id = i.shipment_id WHERE o.dest_hub_id = ? AND o.status IN ('ARRIVED','HANDED_OVER') AND i.status <> 'IN_CONTAINER') AS "unloaded",
                   (SELECT count(*) FROM parcels p WHERE p.hub_id = ?) AS "scanned",
                   (SELECT count(*) FROM amr_robots r WHERE r.hub_id = ? AND r.state NOT IN ('ERROR','STOPPED','FAULT')) AS "amrRunning",
                   (SELECT count(*) FROM amr_robots r WHERE r.hub_id = ? AND r.state IN ('ERROR','STOPPED','FAULT')) AS "amrStopped" """, id, id, id, id, id));
        m.put("work", work);
        // 포트(목적지 센터별) 적재: 롤테이너에 실린 박스 / 그 센터행 허브 박스
        m.put("ports", jdbc.queryForList("""
            SELECT c.center_name AS "name", count(p.parcel_no) AS "total", count(p.unit_id) AS "loaded"
              FROM logistics_centers c LEFT JOIN items i ON i.destination_center_id = c.center_id LEFT JOIN parcels p ON p.item_id = i.item_id AND p.hub_id = ?
             WHERE c.center_type = 'DISTRIBUTION_CENTER' GROUP BY c.center_id, c.center_name ORDER BY c.center_id""", id));
        m.put("outbound", jdbc.queryForList("""
            SELECT 'LH#' || l.assignment_id AS "tripId", c.center_name AS "dest", l.status, l.departed_at AS "departedAt", l.domestic_seal_no AS "sealNo",
                   (SELECT count(*) FROM branch_units u WHERE u.assignment_id = l.assignment_id AND u.status <> 'REJECTED') AS "units", l.planned_units AS "planned"
              FROM shipment_legs l JOIN logistics_centers c ON c.center_id = l.to_center_id
             WHERE l.leg_type = 'HUB_TO_CENTER' AND l.from_center_id = ? AND l.received_at IS NULL AND l.status <> 'CANCELED' ORDER BY l.assignment_id""", center));
        m.put("returns", jdbc.queryForList("SELECT container_no AS \"containerNo\", return_status AS \"status\", return_due_at AS \"dueAt\" FROM transport_orders WHERE dest_hub_id = ? AND return_status IN ('RETURNING_EMPTY','RETURN_ARRIVED')", id));
        // v11: 반납 준비 (인계 완료 오더의 하역 완료 · 공컨 점검 상태)
        m.put("returnReady", jdbc.queryForList("""
            SELECT o.order_id AS "orderId", o.order_no AS "orderNo", o.container_no AS "containerNo", o.status,
                   r.unloaded_at AS "unloadedAt", i.result AS "inspection", i.damage_note AS "damageNote", i.inspected_at AS "inspectedAt", o.return_due_at AS "dueAt",
                   o.return_ready_at AS "returnReadyAt", o.return_instructed_at AS "instructedAt", rd.user_name AS "returnDriverName", o.return_status AS "returnStatus"
              FROM transport_orders o LEFT JOIN accounts rd ON rd.account_id = o.return_driver_account_id LEFT JOIN hub_inbound_receipts r ON r.assignment_id = o.assignment_id AND r.status = 'COMPLETED'
              LEFT JOIN empty_container_inspections i ON i.order_id = o.order_id
             WHERE o.dest_hub_id = ? AND o.status IN ('ARRIVED','HANDED_OVER') AND o.return_status IS NULL ORDER BY o.order_id""", id));
        m.put("problems", jdbc.queryForList("SELECT severity, message, status FROM exceptions WHERE center_id = ? AND status <> 'RESOLVED' ORDER BY severity, occurred_at DESC LIMIT 6", center));
        return m;
    }

    // ------------------------------------------------------------ W-05 지역센터 관제
    @GetMapping("/centers")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','HUB_OP','CENTER_OP','CARRIER')")
    public List<Map<String, Object>> centers() {
        return jdbc.queryForList("SELECT center_id AS \"centerId\", center_name AS \"name\", center_type AS \"type\" FROM logistics_centers WHERE center_type IN ('HUB','DISTRIBUTION_CENTER') ORDER BY center_id");
    }

    @GetMapping("/centers/{id}/monitor")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','CENTER_OP')")
    public Map<String, Object> centerMonitor(@PathVariable("id") Long id) {
        Account a = me();
        if (a.getUserType() == Account.UserType.CENTER_OP && !Objects.equals(a.getCenterId(), id)) throw new AccessDeniedException("소속 센터만 볼 수 있습니다.");
        Map<String, Object> m = new LinkedHashMap<>();
        List<Map<String, Object>> legs = jdbc.queryForList("""
            SELECT l.assignment_id AS "tripId", l.vehicle_no AS "vehicleNo", l.status, l.planned_depart_at AS "plannedDepartAt", l.departed_at AS "departedAt",
                   l.arrived_at AS "arrivedAt", l.inspected_at AS "inspectedAt", l.received_at AS "receivedAt",
                   l.domestic_seal_no AS "domesticSealNo", l.arrival_seal_no AS "arrivalSealNo", l.arrival_seal_checked_at AS "arrivalCheckedAt", l.arrival_seal_checked_by AS "arrivalCheckedBy",
                   (SELECT count(*) FROM branch_seal_checks c WHERE c.assignment_id = l.assignment_id) AS "arrivalChecks",
                   (SELECT max(u.unloaded_at) FROM branch_units u WHERE u.assignment_id = l.assignment_id) AS "unloadedAt",
                   (SELECT count(*) FROM branch_units u WHERE u.assignment_id = l.assignment_id) AS "units"
              FROM shipment_legs l WHERE l.leg_type = 'HUB_TO_CENTER' AND l.to_center_id = ? AND l.status <> 'CANCELED' ORDER BY l.assignment_id DESC LIMIT 20""", id);
        m.put("incoming", legs.stream().filter(x -> x.get("receivedAt") == null).toList());
        m.put("latest", legs.isEmpty() ? null : legs.get(0));
        m.put("sealTrips", legs.stream().filter(x -> x.get("departedAt") != null).toList()); // fix_1300: 지선 봉인 대조 (출발 vs 도착)
        // 허브 게이트인 전 화물 = "입고 예정(해외 배송 중)"
        m.put("expected", jdbc.queryForList("""
            SELECT o.order_no AS "orderNo", o.container_no AS "containerNo", count(i.item_id) AS "boxes" FROM items i JOIN transport_orders o ON o.shipment_id = i.shipment_id
             WHERE i.destination_center_id = ? AND o.status IN ('CREATED','ASSIGNED','LOADED','GATE_OUT','IN_TRANSIT') GROUP BY o.order_no, o.container_no""", id));
        m.put("problems", jdbc.queryForList("SELECT severity, message, status FROM exceptions WHERE center_id = ? AND status <> 'RESOLVED' ORDER BY severity, occurred_at DESC", id));
        return m;
    }

    /** fix_1300: 센터 도착 봉인 확인 (허브 출발 입력과 대조). body { sealNo } */
    @PostMapping("/centers/trips/{id}/arrival-seal")
    @PreAuthorize("hasAnyRole('ADMIN','CENTER_OP')")
    public Map<String, Object> arrivalSeal(@PathVariable("id") Long id, @RequestBody Map<String, String> b) {
        return branch.confirmArrivalSeal(id, b == null ? null : b.get("sealNo"), me());
    }

    // ------------------------------------------------------------ W-06 AMR 관제
    @GetMapping("/amr/robots")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','HUB_OP')")
    public List<Map<String, Object>> robots() {
        Account a = me();
        return jdbc.queryForList("""
            SELECT r.robot_code AS "code", r.robot_type AS "type", r.state, r.battery_pct AS "battery",
                   t.task_type AS "task", COALESCE(fd.dock_code, '') || CASE WHEN td.dock_code IS NOT NULL THEN ' → ' || td.dock_code ELSE '' END AS "place"
              FROM amr_robots r
              LEFT JOIN LATERAL (SELECT * FROM amr_tasks x WHERE x.robot_id = r.robot_id ORDER BY x.task_id DESC LIMIT 1) t ON true
              LEFT JOIN hub_docks fd ON fd.dock_id = t.from_dock_id LEFT JOIN hub_docks td ON td.dock_id = t.to_dock_id
             WHERE (CAST(? AS bigint) IS NULL OR r.hub_id = ?) ORDER BY r.robot_code""", a.getHubId(), a.getHubId());
    }

    // ------------------------------------------------------------ W-07 운송오더·물품 관리
    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','CARRIER')")
    public List<Map<String, Object>> orders() {
        Long co = carrierCompany(me());
        return jdbc.queryForList("""
            SELECT o.order_id AS "orderId", o.order_no AS "orderNo", o.container_no AS "containerNo", o.status, o.return_status AS "returnStatus", o.return_due_at AS "returnDueAt",
                   o.vehicle_no AS "vehicleNo", d.user_name AS "driverName", o.shipper_name AS "shipper", o.return_cy AS "returnCy", o.return_cy_id AS "returnCyId",
                   o.return_ready_at AS "returnReadyAt", o.return_driver_account_id AS "returnDriverId", rd.user_name AS "returnDriverName", o.return_instructed_at AS "instructedAt",
                   o.return_receiver_name AS "returnReceiverName", o.return_eir_no AS "returnEirNo", o.damage_noted AS "damageNoted",
                   (SELECT i.damage_location || ' · ' || i.damage_type FROM empty_container_inspections i WHERE i.order_id = o.order_id AND i.result = 'DAMAGED') AS "damageNote",
                   (SELECT seal_no FROM container_seals s WHERE s.shipment_id = o.shipment_id ORDER BY s.seal_id LIMIT 1) AS "sealNo",
                   (SELECT count(*) FROM items i WHERE i.shipment_id = o.shipment_id) AS "boxes",
                   (o.return_due_at IS NOT NULL AND o.empty_returned_at IS NULL AND o.return_due_at <= now() + make_interval(hours => ?)) AS "dueSoon"
              FROM transport_orders o LEFT JOIN accounts d ON d.account_id = o.driver_account_id LEFT JOIN accounts rd ON rd.account_id = o.return_driver_account_id
             WHERE (CAST(? AS bigint) IS NULL OR o.company_id = ?) ORDER BY o.order_id DESC""", setting("RETURN_DUE_SOON_HOURS", 24), co, co);
    }

    /** 등록 폼 선택지: 소속 기사·차량, 터미널, 허브, 지역센터, 출고 도크 */
    @GetMapping("/carrier/form-options")
    @PreAuthorize("hasRole('CARRIER')")
    public Map<String, Object> formOptions() {
        Long co = me().getCompanyId();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("drivers", jdbc.queryForList("SELECT DISTINCT ON (a.account_id) a.account_id AS \"accountId\", a.user_name AS \"userName\", a.user_type AS \"userType\", t.vehicle_no AS \"vehicleNo\" FROM accounts a LEFT JOIN trucks t ON t.assigned_account_id = a.account_id AND t.company_id = a.company_id WHERE a.company_id = ? AND a.user_type IN ('CONTAINER_DRIVER','BRANCH_DRIVER') AND a.approval_status = 'APPROVED' ORDER BY a.account_id, t.vehicle_no", co));
        m.put("vehicles", jdbc.queryForList("SELECT vehicle_no AS \"vehicleNo\", truck_type AS \"truckType\", max_load_ton AS \"maxLoadTon\", assigned_account_id AS \"driverId\", vehicle_category AS \"category\" FROM trucks WHERE company_id = ? ORDER BY vehicle_no", co));
        m.put("terminals", jdbc.queryForList("SELECT terminal_id AS \"id\", terminal_name AS \"name\" FROM terminals WHERE is_active IS NOT FALSE ORDER BY terminal_id"));
        m.put("hubs", jdbc.queryForList("SELECT hub_id AS \"id\", hub_name AS \"name\", center_id AS \"centerId\" FROM hubs ORDER BY hub_id"));
        m.put("centers", jdbc.queryForList("SELECT center_id AS \"id\", center_name AS \"name\" FROM logistics_centers WHERE center_type = 'DISTRIBUTION_CENTER' ORDER BY center_id"));
        m.put("outDocks", jdbc.queryForList("SELECT dock_id AS \"id\", dock_code AS \"name\", hub_id AS \"hubId\" FROM hub_docks WHERE dock_type = 'OUT' ORDER BY dock_code"));
        return m;
    }

    /** 운송오더 등록. body: containerNo, shipper, sealNo(필수), terminalId, hubId, returnCy, returnDueAt, driverAccountId, vehicleNo
     *  기사·차량을 지정하면 PORT_TO_HUB 구간을 만들고 ASSIGNED, 없으면 CREATED */
    @PostMapping("/carrier/orders")
    @PreAuthorize("hasRole('CARRIER')")
    @Transactional
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> b) {
        Account a = me(); Long co = a.getCompanyId();
        String cno = str(b.get("containerNo")); String seal = str(b.get("sealNo"));
        if (cno == null || !cno.toUpperCase().matches("[A-Z]{4}\\d{7}")) throw new IllegalArgumentException("컨테이너 번호는 영문 4자 + 숫자 7자입니다.");
        if (seal == null || seal.isBlank()) throw new IllegalArgumentException("출발 씰번호를 입력하세요.");
        cno = cno.toUpperCase();
        Long hubId = Optional.ofNullable(staffNum(b.get("hubId"))).orElse(1L);
        Long terminalId = Optional.ofNullable(num(b.get("terminalId"))).orElseGet(() -> jdbc.queryForObject("SELECT min(terminal_id) FROM terminals", Long.class));
        Long portCenter = jdbc.queryForObject("SELECT COALESCE(center_id, (SELECT center_id FROM logistics_centers WHERE center_type='PORT' ORDER BY center_id LIMIT 1)) FROM terminals WHERE terminal_id = ?", Long.class, terminalId);
        Long hubCenter = jdbc.queryForObject("SELECT center_id FROM hubs WHERE hub_id = ?", Long.class, hubId);
        String orderNo = "TO-" + java.time.LocalDate.now().toString().replace("-", "") + "-" + String.format("%03d", jdbc.queryForObject("SELECT count(*) + 1 FROM transport_orders WHERE created_at::date = current_date", Long.class));
        // fix_1330 씰 정책: 정답 씰은 DB 에 미리 등록된 container_seals 만. 오더 등록은 그 씰을 선택·검증만 하고 새 씰·새 화물(shipment)을 만들지 않는다.
        //  컨테이너의 ACTIVE 씰이 없거나, 입력 씰 번호가 다르거나, 그 씰 화물이 이미 다른 진행 중 오더에 쓰이면 거절
        List<Map<String, Object>> ref = jdbc.queryForList("SELECT seal_id, shipment_id, seal_no FROM container_seals WHERE container_no = ? AND status = 'ACTIVE' ORDER BY seal_id DESC LIMIT 1", cno);
        if (ref.isEmpty()) throw new IllegalArgumentException("등록된 씰 정보가 없습니다 (" + cno + "). 씰 목록에서 선택하세요.");
        // 26.10.29 QA: 씰은 화면에서 직접 입력 → 공백 · 하이픈 · 대소문자 차이는 같은 번호로 보고 비교. 다르면 저장 막음 (정답 씰 번호는 알려 주지 않음)
        if (!sealKey(seal).equals(sealKey((String) ref.get(0).get("seal_no")))) throw new IllegalArgumentException("선택한 컨테이너(" + cno + ")의 등록 씰과 다릅니다. 컨테이너에 붙은 씰 번호를 다시 확인하세요.");
        Long sid = ((Number) ref.get(0).get("shipment_id")).longValue();
        if (jdbc.queryForObject("SELECT count(*) FROM transport_orders WHERE shipment_id = ? AND status <> 'CANCELED'", Integer.class, sid) > 0)
            throw new IllegalStateException("이 씰(" + ref.get(0).get("seal_no") + ")은 이미 다른 운송오더에 연결되어 있습니다.");
        jdbc.update("UPDATE shipments SET destination_center_id = COALESCE(destination_center_id, ?), updated_at = now() WHERE shipment_id = ?", hubCenter, sid);
        Long driver = num(b.get("driverAccountId")); String vehicle = str(b.get("vehicleNo"));
        // v11: 반납 CY 는 return_cys 목록에서만 (자유 입력 불가)
        if (b.get("returnCy") != null && b.get("returnCyId") == null && !str(b.get("returnCy")).isEmpty()) throw new IllegalArgumentException("반납 CY 는 목록에서 선택하세요 (returnCyId).");
        Map<String, Object> cy = activeCy(num(b.get("returnCyId")));
        OffsetDateTime due = b.get("returnDueAt") == null || str(b.get("returnDueAt")).isBlank() ? null : OffsetDateTime.parse(str(b.get("returnDueAt")).length() == 16 ? str(b.get("returnDueAt")) + ":00+09:00" : str(b.get("returnDueAt")));
        // fix_1300: 반납 기한이 비면 기본값 (오늘 + RETURN_DUE_DEFAULT_DAYS(기본 7)일, 18:00 KST). 기한 없는 오더는 임박/초과 예외가 안 생겨 놓침
        boolean dueDefaulted = due == null;
        if (dueDefaulted) due = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(setting("RETURN_DUE_DEFAULT_DAYS", 7)).atTime(18, 0).atOffset(java.time.ZoneOffset.ofHours(9));
        Long leg = null; String status = "CREATED";
        if (driver != null && vehicle != null && !vehicle.isBlank()) {
            Integer okD = jdbc.queryForObject("SELECT count(*) FROM accounts WHERE account_id = ? AND company_id = ? AND user_type = 'CONTAINER_DRIVER' AND approval_status = 'APPROVED'", Integer.class, driver, co);
            Integer okV = jdbc.queryForObject("SELECT count(*) FROM trucks WHERE vehicle_no = ? AND company_id = ?", Integer.class, vehicle, co);
            if (okD == 0 || okV == 0) throw new AccessDeniedException("우리 운송사의 승인된 컨테이너 기사·차량만 배차할 수 있습니다.");
            leg = jdbc.queryForObject("INSERT INTO shipment_legs(shipment_id, vehicle_no, from_center_id, to_center_id, leg_sequence, leg_type, status, driver_account_id) VALUES (?,?,?,?,1,'PORT_TO_HUB','ASSIGNED',?) RETURNING assignment_id",
                    Long.class, sid, vehicle, portCenter, hubCenter, driver);
            status = "ASSIGNED";
        }
        Long id = jdbc.queryForObject("""
            INSERT INTO transport_orders(order_no, company_id, container_no, vehicle_no, driver_account_id, origin_terminal_id, dest_hub_id, status, shipment_id, assignment_id, assigned_at, return_cy, return_due_at, shipper_name)
            VALUES (?,?,?,?,?,?,?,?,?,?, CASE WHEN ? = 'ASSIGNED' THEN now() END, ?,?,?) RETURNING order_id""",
                Long.class, orderNo, co, cno, leg == null ? null : vehicle, leg == null ? null : driver, terminalId, hubId, status, sid, leg, status, cy == null ? null : cy.get("cy_name"), due, str(b.get("shipper")));
        if (cy != null) jdbc.update("UPDATE transport_orders SET return_cy_id = ?, return_cy = ? WHERE order_id = ?", cy.get("cy_id"), cy.get("cy_name"), id);   // v12: CY 이름 스냅샷
        return Map.of("orderId", id, "orderNo", orderNo, "status", status, "shipmentId", sid, "returnDueAt", due.toString(), "returnDueDefaulted", dueDefaulted);
    }

    private static String sealKey(String v) { return v == null ? "" : v.replaceAll("[\\s-]", "").toUpperCase(); }

    // ------------------------------------------------------------ v11 반납 CY 목록 · 반납 계획 수정 · 하역 완료 · 공컨 점검
    private Map<String, Object> activeCy(Long cyId) {
        if (cyId == null) return null;
        List<Map<String, Object>> l = jdbc.queryForList("SELECT cy_id, cy_name FROM return_cys WHERE cy_id = ? AND is_active", cyId);
        if (l.isEmpty()) throw new IllegalArgumentException("반납 CY 목록에 없는 값입니다.");
        return l.get(0);
    }

    /** fix_1330: W-07 오더 등록용 선택 목록 = DB 에 등록된 ACTIVE 씰 중 진행 중 오더에 연결되지 않은 것
     *  26.10.29 QA: 씰 번호는 화면에서 직접 입력하므로 목록에는 컨테이너만 내려 준다 (정답 씰 번호 노출 안 함) */
    @GetMapping("/carrier/seal-options")
    @PreAuthorize(W02)
    public List<Map<String, Object>> sealOptions() {
        return jdbc.queryForList("""
            SELECT s.seal_id AS "sealId", s.container_no AS "containerNo"
              FROM container_seals s
             WHERE s.status = 'ACTIVE'
               AND NOT EXISTS (SELECT 1 FROM transport_orders o WHERE o.shipment_id = s.shipment_id AND o.status <> 'CANCELED')
             ORDER BY s.container_no""");
    }

    @GetMapping("/return-cys")
    @PreAuthorize(W02)
    public List<Map<String, Object>> returnCys() {
        return jdbc.queryForList("SELECT cy_id AS \"id\", cy_code AS \"code\", cy_name AS \"name\", address, is_demo AS \"isDemo\" FROM return_cys WHERE is_active ORDER BY sort_order, cy_id");
    }

    /** 등록 후 반납 기한·반납 CY 수정 (운송사 = 자기 회사 오더, ADMIN). body: returnCyId?, returnDueAt?, reason(필수)
     *  v11: return_plan_changes 이력. 반납 시작 뒤 CY 변경 → RETURN_CY_CHANGED(WARNING) 예외 + 기사 즉시 알림(확인 필요) */
    @PutMapping("/carrier/orders/{id}/return-plan")
    @PreAuthorize("hasAnyRole('ADMIN','CARRIER')")
    @Transactional
    public Map<String, Object> editReturnPlan(@PathVariable("id") Long id, @RequestBody Map<String, Object> b) {
        ownOrder(id);
        Map<String, Object> o = jdbc.queryForMap("SELECT o.order_no, o.container_no, o.company_id, o.driver_account_id, o.return_driver_account_id, o.return_status, o.return_cy_id, o.return_cy, o.return_due_at, o.status, o.assignment_id, h.center_id FROM transport_orders o LEFT JOIN hubs h ON h.hub_id = o.dest_hub_id WHERE o.order_id = ?", id);
        if ("EMPTY_RETURNED".equals(o.get("return_status"))) throw new IllegalStateException("반납이 끝난(종료된) 오더는 바꿀 수 없습니다.");
        if ("CANCELED".equals(o.get("status"))) throw new IllegalStateException("취소된 오더입니다.");
        String reason = str(b.get("reason"));
        if (reason == null || reason.isEmpty()) throw new IllegalArgumentException("변경 사유를 입력하세요.");
        String rs = (String) o.get("return_status");
        Object driver = o.get("return_driver_account_id") != null ? o.get("return_driver_account_id") : o.get("driver_account_id");
        List<String> changes = new ArrayList<>();
        Long cyChangeId = null;
        if (b.containsKey("returnCyId")) {
            Map<String, Object> cy = activeCy(num(b.get("returnCyId")));
            if (cy == null) throw new IllegalArgumentException("반납 CY 를 선택하세요.");
            if (!Objects.equals(num(o.get("return_cy_id")), num(cy.get("cy_id")))) {
                jdbc.update("UPDATE transport_orders SET return_cy_id = ?, return_cy = ?, updated_at = now() WHERE order_id = ?", cy.get("cy_id"), cy.get("cy_name"), id);
                changes.add("반납 CY " + (o.get("return_cy") == null ? "-" : o.get("return_cy")) + " → " + cy.get("cy_name"));
                boolean started = "RETURNING_EMPTY".equals(rs) || "RETURN_ARRIVED".equals(rs);   // v12: 손상 보류 상태는 시작 아님
                cyChangeId = jdbc.queryForObject("""
                    INSERT INTO return_plan_changes(order_id, field, old_value, new_value, old_cy_id, new_cy_id, reason, changed_by_account_id, return_status_at_change, driver_account_id)
                    VALUES (?, 'CY', ?, ?, ?, ?, ?, ?, ?, ?) RETURNING change_id""", Long.class,
                        id, o.get("return_cy"), cy.get("cy_name"), o.get("return_cy_id"), cy.get("cy_id"), reason, CurrentAccount.id(), rs, started ? o.get("return_driver_account_id") : null);   // v12: 반납 기사(CONTAINER_DRIVER)
                if (started) {
                    String m = o.get("container_no") + " 반납지 변경: " + (o.get("return_cy") == null ? "-" : o.get("return_cy")) + " → " + cy.get("cy_name") + " (사유: " + reason + ")";
                    jdbc.update("""
                        INSERT INTO exceptions(exception_type, severity, status, center_id, assignment_id, source_table, source_key, message, occurred_at)
                        VALUES ('RETURN_CY_CHANGED', 'WARNING', 'OPEN', NULL, ?, 'return_plan_changes', ?, ?, now())""",   // v12: center_id 는 DB 트리거가 채움
                            o.get("assignment_id"), String.valueOf(cyChangeId), m);
                    if (driver != null)
                        jdbc.update("INSERT INTO notifications(account_id, title, message, notification_type, reference_id) VALUES (?,?,?, 'RETURN_CY_CHANGED', ?)",
                                driver, "[반납지 변경 · 확인 필요] " + o.get("container_no"), m, cyChangeId);
                } else cyChangeId = null;
            }
        }
        if (b.containsKey("returnDueAt")) {
            String v = str(b.get("returnDueAt"));
            if (v == null || v.isEmpty()) throw new IllegalArgumentException("반납 기한을 입력하세요.");
            OffsetDateTime due = OffsetDateTime.parse(v.length() == 16 ? v + ":00+09:00" : v);
            String fmt = "to_char(? AT TIME ZONE 'Asia/Seoul', 'MM/DD HH24:MI')";
            String before = o.get("return_due_at") == null ? "-" : jdbc.queryForObject("SELECT " + fmt, String.class, o.get("return_due_at"));
            String after = jdbc.queryForObject("SELECT " + fmt, String.class, due);
            if (jdbc.update("UPDATE transport_orders SET return_due_at = ?, updated_at = now() WHERE order_id = ? AND return_due_at IS DISTINCT FROM ?", due, id, due) > 0) {
                jdbc.update("INSERT INTO return_plan_changes(order_id, field, old_value, new_value, old_due_at, new_due_at, reason, changed_by_account_id, return_status_at_change) VALUES (?, 'DUE', ?, ?, ?, ?, ?, ?, ?)",
                        id, before, after, o.get("return_due_at"), due, reason, CurrentAccount.id(), rs);
                if (!before.equals(after)) changes.add("반납 기한 " + before + " → " + after);
            }
        }
        if (!changes.isEmpty()) {
            String title = "[반납 정보 변경] " + o.get("container_no"), msg = o.get("order_no") + " · " + String.join(" · ", changes) + " (사유: " + reason + ")";
            // v12 (14-4): 기한만 바뀌면 기사 앱 알림만. CY 가 바뀌면 운송사에도
            boolean cyChanged = changes.stream().anyMatch(c -> c.startsWith("반납 CY"));
            if (cyChanged) jdbc.update("INSERT INTO notifications(company_id, title, message, notification_type, reference_id) VALUES (?,?,?, 'RETURN_PLAN_CHANGED', ?)", o.get("company_id"), title, msg, id);
            if (driver != null)
                jdbc.update("INSERT INTO notifications(account_id, title, message, notification_type, reference_id) VALUES (?,?,?, 'RETURN_PLAN_CHANGED', ?)", driver, title, msg, id);
        }
        Map<String, Integer> sync = returnDue.syncExceptions(id);   // 기한이 바뀌면 임박/초과 예외 재계산
        Map<String, Object> r = new LinkedHashMap<>(jdbc.queryForMap("SELECT order_id AS \"orderId\", return_cy_id AS \"returnCyId\", return_cy AS \"returnCy\", return_due_at AS \"returnDueAt\" FROM transport_orders WHERE order_id = ?", id));
        r.put("changes", changes); r.put("notified", !changes.isEmpty()); r.put("cyChangeId", cyChangeId); r.put("sync", sync);
        return r;
    }

    /** v12: 운송오더 삭제 = 취소 (status CANCELED). 점검 기록·변경 이력이 RESTRICT 로 묶여 있어 행은 지우지 않음 */
    @DeleteMapping("/carrier/orders/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CARRIER')")
    @Transactional
    public Map<String, Object> cancelOrder(@PathVariable("id") Long id) {
        ownOrder(id);
        Map<String, Object> o = jdbc.queryForMap("SELECT status, return_status, assignment_id FROM transport_orders WHERE order_id = ?", id);
        if ("CANCELED".equals(o.get("status"))) throw new IllegalStateException("이미 취소된 오더입니다.");
        if (List.of("RETURNING_EMPTY", "RETURN_ARRIVED", "EMPTY_RETURNED").contains(String.valueOf(o.get("return_status")))) throw new IllegalStateException("공컨 반납이 시작됐거나 끝난 오더는 취소할 수 없습니다.");
        if (List.of("ARRIVED", "HANDED_OVER").contains(String.valueOf(o.get("status")))) throw new IllegalStateException("허브에 도착한 오더는 취소할 수 없습니다.");
        jdbc.update("UPDATE transport_orders SET status = 'CANCELED', updated_at = now() WHERE order_id = ?", id);
        if (o.get("assignment_id") != null) jdbc.update("UPDATE shipment_legs SET status = 'CANCELED' WHERE assignment_id = ? AND status NOT IN ('ARRIVED','RECEIVED')", o.get("assignment_id"));
        returnDue.syncExceptions(id);   // 반납 기한 예외 '운송오더 취소 (자동 해결)'
        return Map.of("orderId", id, "status", "CANCELED");
    }

    /** v12: 반납 첨부 저장 (EIR_IN / DAMAGE_CONFIRM). 업로드 권한은 DB 트리거가 최종 판정 (오더 운송사 계정·반납 기사, 위반 23514 → 409) */
    static Map<String, Object> saveAttachment(JdbcTemplate jdbc, Long id, String kind, MultipartFile file, Long me) throws Exception {
        if (!com.scargo.ops.entity.ReturnAttachment.KINDS.contains(kind)) throw new IllegalArgumentException("첨부 종류는 EIR_IN 또는 DAMAGE_CONFIRM");
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("파일을 올려 주세요.");
        if (jdbc.queryForObject("SELECT count(*) FROM transport_orders WHERE order_id = ?", Integer.class, id) == 0) throw new IllegalArgumentException("오더가 없습니다.");
        String ext = com.scargo.config.UploadStorage.checkedExt(file); // fix_1300: jpg·png·pdf 만
        String name = id + "_" + kind + "_" + System.currentTimeMillis() + ext;
        java.nio.file.Path dir = com.scargo.config.UploadStorage.dir("return-attachments");
        String url = "/uploads/return-attachments/" + name;
        Long aid = jdbc.queryForObject("INSERT INTO return_attachments(order_id, kind, file_url, uploaded_by_account_id) VALUES (?,?,?,?) RETURNING attachment_id", Long.class, id, kind, url, me);
        file.transferTo(dir.resolve(name).toAbsolutePath());
        return Map.of("attachmentId", aid, "kind", kind, "fileUrl", url);
    }

    static List<Map<String, Object>> attachments(JdbcTemplate jdbc, Long id) {
        return jdbc.queryForList("SELECT t.attachment_id AS \"attachmentId\", t.kind, t.file_url AS \"fileUrl\", a.user_name AS \"uploadedBy\", t.uploaded_at AS \"uploadedAt\" FROM return_attachments t LEFT JOIN accounts a ON a.account_id = t.uploaded_by_account_id WHERE t.order_id = ? ORDER BY t.attachment_id", id);
    }

    /** v12: 반납 첨부 업로드 (운송사). 같은 회사 여부는 DB 가 판정 → 다른 회사·ADMIN 은 409 */
    @PostMapping("/carrier/orders/{id}/attachments")
    @PreAuthorize("hasAnyRole('ADMIN','CARRIER')")
    @Transactional
    public Map<String, Object> uploadAttachment(@PathVariable("id") Long id, @RequestParam("kind") String kind, @RequestParam("file") MultipartFile file) throws Exception {
        return saveAttachment(jdbc, id, kind, file, CurrentAccount.id());
    }

    @GetMapping("/orders/{id}/attachments")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','CARRIER')")
    public List<Map<String, Object>> listAttachments(@PathVariable("id") Long id) { ownOrder(id); return attachments(jdbc, id); }

    /** v11: 반납 CY·기한 변경 이력 */
    @GetMapping("/orders/{id}/return-history")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','CARRIER')")
    public List<Map<String, Object>> returnHistory(@PathVariable("id") Long id) {
        ownOrder(id);
        return jdbc.queryForList("""
            SELECT c.change_id AS "changeId", c.field, c.old_value AS "oldValue", c.new_value AS "newValue", c.reason, c.changed_at AS "changedAt",
                   a.user_name AS "changedBy", c.return_status_at_change AS "returnStatus", c.driver_confirmed_at AS "driverConfirmedAt", c.driver_account_id IS NOT NULL AS "needsConfirm"
              FROM return_plan_changes c LEFT JOIN accounts a ON a.account_id = c.changed_by_account_id WHERE c.order_id = ? ORDER BY c.change_id DESC""", id);
    }

    /** v11: 반납 지시 (운송사 승인 담당자 = 같은 회사 CORPORATE_APPROVED, 또는 ADMIN). body: driverAccountId */
    @PostMapping("/carrier/orders/{id}/return-instruction")
    @PreAuthorize("hasAnyRole('ADMIN','CARRIER')")
    @Transactional
    public Map<String, Object> returnInstruction(@PathVariable("id") Long id, @RequestBody Map<String, Object> b) {
        Account a = me();
        Map<String, Object> o = jdbc.queryForMap("SELECT order_no, container_no, company_id, return_ready_at, return_status, return_cy, return_due_at FROM transport_orders WHERE order_id = ?", id);
        if (a.getUserType() != Account.UserType.ADMIN) {
            Long co = carrierCompany(a);
            if (co == null || !Objects.equals(co, num(o.get("company_id")))) throw new AccessDeniedException("같은 운송사의 승인 담당자만 반납을 지시할 수 있습니다.");
        }
        if (o.get("return_ready_at") == null) throw new IllegalStateException("반납 가능 상태가 아닙니다 (허브 하역 완료·공컨 점검 OK 필요).");
        if (o.get("return_status") != null) throw new IllegalStateException("이미 반납 운행이 시작된 오더입니다.");
        Long driverId = num(b.get("driverAccountId"));
        if (driverId == null) throw new IllegalArgumentException("반납 기사를 선택하세요.");
        Integer ok = jdbc.queryForObject("SELECT count(*) FROM accounts WHERE account_id = ? AND user_type = 'CONTAINER_DRIVER' AND approval_status = 'APPROVED' AND company_id = ?", Integer.class, driverId, o.get("company_id"));
        if (ok == null || ok == 0) throw new IllegalArgumentException("같은 회사의 승인된 컨테이너 기사만 지정할 수 있습니다.");
        jdbc.update("UPDATE transport_orders SET return_driver_account_id = ?, return_instructed_at = now(), return_instructed_by_account_id = ?, updated_at = now() WHERE order_id = ?", driverId, a.getAccountId(), id);
        String due = o.get("return_due_at") == null ? "-" : jdbc.queryForObject("SELECT to_char(? AT TIME ZONE 'Asia/Seoul', 'MM/DD HH24:MI')", String.class, o.get("return_due_at"));
        jdbc.update("INSERT INTO notifications(account_id, title, message, notification_type, reference_id) VALUES (?,?,?, 'CONTAINER_RETURN_INSTRUCTED', ?)",
                driverId, "[반납 지시] " + o.get("container_no"), o.get("order_no") + " 공컨을 " + (o.get("return_cy") == null ? "반납 CY(앱에서 선택)" : o.get("return_cy")) + " 로 반납하세요 · 기한 " + due, id);
        return Map.of("orderId", id, "returnDriverId", driverId, "instructed", true);
    }

    /** v11: 하역 완료 + 공컨 점검 OK → 반납 가능 (return_ready_at) + 운송사 알림 */
    private boolean markReturnReady(Long id) {
        int n = jdbc.update("""
            UPDATE transport_orders o SET return_ready_at = now(), updated_at = now()
             WHERE o.order_id = ? AND o.return_ready_at IS NULL AND o.return_status IS NULL
               AND EXISTS (SELECT 1 FROM hub_inbound_receipts r WHERE r.assignment_id = o.assignment_id AND r.status = 'COMPLETED' AND r.unloaded_at IS NOT NULL)
               AND EXISTS (SELECT 1 FROM empty_container_inspections i WHERE i.order_id = o.order_id)   -- v12: 손상이어도 반납 가능""", id);
        if (n > 0) {
            Map<String, Object> o = jdbc.queryForMap("SELECT order_no, container_no, company_id FROM transport_orders WHERE order_id = ?", id);
            jdbc.update("INSERT INTO notifications(company_id, title, message, notification_type, reference_id) VALUES (?,?,?, 'CONTAINER_RETURN_READY', ?)",
                    o.get("company_id"), "[반납 가능] " + o.get("container_no"), o.get("order_no") + " 허브 하역·공컨 점검이 끝났습니다. 반납 기사를 지정해 주세요.", id);
        }
        return n > 0;
    }

    private Map<String, Object> hubOrder(Long id) {
        Account a = me();
        Map<String, Object> o = jdbc.queryForMap("SELECT order_id, dest_hub_id, assignment_id, vehicle_no, container_no, status, return_status FROM transport_orders WHERE order_id = ?", id);
        if (a.getUserType() == Account.UserType.HUB_OP && !Objects.equals(a.getHubId(), num(o.get("dest_hub_id")))) throw new AccessDeniedException("소속 허브 오더만 처리할 수 있습니다.");
        if (!List.of("ARRIVED", "HANDED_OVER").contains(String.valueOf(o.get("status")))) throw new IllegalStateException("허브 도착 후에 처리할 수 있습니다.");
        return o;
    }

    /** 허브 컨테이너 하역 완료 → hub_inbound_receipts(COMPLETED, unloaded_at). 기존 표 사용 */
    @PostMapping("/hubs/orders/{id}/unload-complete")
    @PreAuthorize("hasAnyRole('ADMIN','HUB_OP')")
    @Transactional
    public Map<String, Object> unloadComplete(@PathVariable("id") Long id) {
        Map<String, Object> o = hubOrder(id);
        if (o.get("assignment_id") == null) throw new IllegalStateException("배차 구간이 없는 오더입니다.");
        jdbc.update("""
            INSERT INTO hub_inbound_receipts(assignment_id, hub_id, vehicle_no, container_no, status, unloaded_at) VALUES (?,?,?,?, 'COMPLETED', now())
            ON CONFLICT (assignment_id) DO UPDATE SET status = 'COMPLETED', unloaded_at = COALESCE(hub_inbound_receipts.unloaded_at, now())""",
                o.get("assignment_id"), o.get("dest_hub_id"), o.get("vehicle_no"), o.get("container_no"));
        return Map.of("orderId", id, "unloaded", true, "returnReady", markReturnReady(id));
    }

    /** 공컨 상태 점검 (JSON). OK 는 JSON 으로 가능, DAMAGED 는 사진이 필요해 multipart 로 */
    @PostMapping(value = "/hubs/orders/{id}/empty-inspection", consumes = "application/json")
    @PreAuthorize("hasAnyRole('ADMIN','HUB_OP')")
    @Transactional
    public Map<String, Object> emptyInspection(@PathVariable("id") Long id, @RequestBody Map<String, Object> b) throws Exception {
        return inspect(id, str(b.get("result")), str(b.get("note")), str(b.get("damageLocation")), str(b.get("damageType")), null);
    }

    /** 공컨 상태 점검 (multipart). result, note?, damageLocation, damageType, photo — DAMAGED 면 위치·유형·사진(컨테이너 번호 보이게) 필수 */
    @PostMapping(value = "/hubs/orders/{id}/empty-inspection", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN','HUB_OP')")
    @Transactional
    public Map<String, Object> emptyInspectionForm(@PathVariable("id") Long id, @RequestParam("result") String result, @RequestParam(name = "note", required = false) String note,
                                                   @RequestParam(name = "damageLocation", required = false) String damageLocation, @RequestParam(name = "damageType", required = false) String damageType,
                                                   @RequestParam(value = "photo", required = false) MultipartFile photo) throws Exception {
        return inspect(id, result, note, damageLocation, damageType, photo);
    }

    /** v12 (통합 SQL 14-5 사용자 확정): 손상은 기록·알림·WARNING 예외만. 반납은 막지 않음 (점검 완료만 필요) */
    private Map<String, Object> inspect(Long id, String result, String note, String loc, String type, MultipartFile photo) throws Exception {
        Map<String, Object> o = hubOrder(id);
        if (o.get("return_status") != null) throw new IllegalStateException("이미 반납 운행이 시작된 오더입니다.");
        if (jdbc.queryForObject("SELECT return_driver_account_id IS NOT NULL FROM transport_orders WHERE order_id = ?", Boolean.class, id)) throw new IllegalStateException("반납 지시가 끝난 오더는 다시 점검할 수 없습니다.");
        Integer unloaded = jdbc.queryForObject("SELECT count(*) FROM hub_inbound_receipts WHERE assignment_id = ? AND status = 'COMPLETED' AND unloaded_at IS NOT NULL", Integer.class, o.get("assignment_id"));
        if (unloaded == null || unloaded == 0) throw new IllegalStateException("하역 완료 후에 공컨 점검을 합니다.");
        if (!List.of("OK", "DAMAGED").contains(result)) throw new IllegalArgumentException("점검 결과는 OK 또는 DAMAGED");
        String url = null;
        if ("DAMAGED".equals(result)) {
            if (loc == null || loc.isBlank()) throw new IllegalArgumentException("손상 위치를 입력하세요.");
            if (type == null || type.isBlank()) throw new IllegalArgumentException("손상 유형을 입력하세요.");
            if (photo == null || photo.isEmpty()) throw new IllegalArgumentException("손상 사진(컨테이너 번호가 보이게)을 올려 주세요.");
        }
        if (photo != null && !photo.isEmpty()) {
            url = com.scargo.config.UploadStorage.save("inspections", id + "_" + System.currentTimeMillis(), photo); // fix_1300
        }
        boolean ok = "OK".equals(result);
        Long inspId = jdbc.queryForObject("""
            INSERT INTO empty_container_inspections(order_id, container_no, result, damage_note, damage_location, damage_type, photo_url, inspected_by_account_id, inspected_at)
            VALUES (?,?,?,?,?,?,?,?, now())
            ON CONFLICT (order_id) DO UPDATE SET result = EXCLUDED.result, damage_note = EXCLUDED.damage_note, damage_location = EXCLUDED.damage_location,
                   damage_type = EXCLUDED.damage_type, photo_url = EXCLUDED.photo_url, inspected_by_account_id = EXCLUDED.inspected_by_account_id, inspected_at = now()
            RETURNING inspection_id""", Long.class,
                id, o.get("container_no"), result, note, ok ? null : loc.trim(), ok ? null : type.trim(), url, CurrentAccount.id());
        if (!ok) {
            String m = o.get("container_no") + " 공컨 손상 · " + loc.trim() + " · " + type.trim() + (note == null || note.isBlank() ? "" : " · " + note.trim());
            // 예외 CONTAINER_DAMAGED (WARNING 고정, center_id 는 DB 트리거가 채움). 같은 점검 행이면 내용 갱신·다시 열기
            jdbc.update("""
                INSERT INTO exceptions(exception_type, severity, status, center_id, assignment_id, source_table, source_key, message, occurred_at)
                VALUES ('CONTAINER_DAMAGED', 'WARNING', 'OPEN', NULL, ?, 'empty_container_inspections', ?, ?, now())
                ON CONFLICT (source_table, source_key, exception_type) DO UPDATE SET message = EXCLUDED.message, status = 'OPEN', resolved_at = NULL, resolution_note = NULL, occurred_at = now()""",
                    o.get("assignment_id"), String.valueOf(inspId), m);
            notifyCarrierContacts(id, "CONTAINER_DAMAGED", "[공컨 손상] ", " " + m + " · 반납은 그대로 진행됩니다 (기록용).");
        } else {
            jdbc.update("UPDATE exceptions SET status = 'RESOLVED', resolved_at = now(), resolution_note = '재점검 OK (자동 해결)' WHERE exception_type = 'CONTAINER_DAMAGED' AND source_table = 'empty_container_inspections' AND source_key = ? AND status <> 'RESOLVED'", String.valueOf(inspId));
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("orderId", id); r.put("inspection", result); r.put("inspectionId", inspId);
        r.put("damageNoted", jdbc.queryForObject("SELECT damage_noted FROM transport_orders WHERE order_id = ?", Boolean.class, id));   // DB 트리거가 설정
        r.put("returnReady", markReturnReady(id));
        return r;
    }

    /** 운송사 승인 담당자(CORPORATE_APPROVED) 전원에게 알림 */
    private void notifyCarrierContacts(Long id, String type, String titlePrefix, String body) {
        Map<String, Object> o = jdbc.queryForMap("SELECT order_no, container_no, company_id FROM transport_orders WHERE order_id = ?", id);
        jdbc.update("INSERT INTO notifications(account_id, title, message, notification_type, reference_id) SELECT a.account_id, ?, ?, ?, ? FROM accounts a WHERE a.company_id = ? AND a.user_type = 'CORPORATE_APPROVED'",
                titlePrefix + o.get("container_no"), o.get("order_no") + body, type, id, o.get("company_id"));
    }


    private Map<String, Object> ownOrder(Long id) {
        Long co = carrierCompany(me());
        Map<String, Object> o = jdbc.queryForMap("SELECT order_id, company_id, shipment_id, container_no FROM transport_orders WHERE order_id = ?", id);
        if (co != null && !Objects.equals(num(o.get("company_id")), co)) throw new AccessDeniedException("다른 운송사의 오더입니다.");
        return o;
    }

    @GetMapping("/orders/{id}/boxes")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','CARRIER')")
    public Map<String, Object> boxes(@PathVariable("id") Long id) {
        Map<String, Object> o = ownOrder(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("boxes", jdbc.queryForList("SELECT i.tracking_no AS \"boxId\", i.item_name AS \"itemName\", i.quantity, c.center_name AS \"dest\" FROM items i LEFT JOIN logistics_centers c ON c.center_id = i.destination_center_id WHERE i.shipment_id = ? ORDER BY i.item_id LIMIT 200", o.get("shipment_id")));
        m.put("byCenter", jdbc.queryForList("SELECT c.center_name AS \"dest\", count(*) AS \"boxes\" FROM items i LEFT JOIN logistics_centers c ON c.center_id = i.destination_center_id WHERE i.shipment_id = ? GROUP BY c.center_name ORDER BY c.center_name", o.get("shipment_id")));
        return m;
    }

    @GetMapping(value = "/carrier/boxes-template", produces = "text/csv; charset=UTF-8")
    @PreAuthorize("hasAnyRole('ADMIN','CARRIER')")
    public ResponseEntity<byte[]> template() {
        String csv = "\uFEFFbox_id,item_name,quantity,weight_kg,dest_center\nBX-0001,생활용품 A,24,12.5,서울\nBX-0002,생활용품 B,12,8,경기\nBX-0003,식품 C,36,20,인천\n";
        return ResponseEntity.ok().header("Content-Disposition", "attachment; filename=packing_template.csv").body(csv.getBytes(StandardCharsets.UTF_8));
    }

    /** 포장명세서·배분지시 CSV 업로드 → items(container_boxes) 반영. 열: box_id,item_name,quantity,weight_kg,dest_center(서울·경기·인천 또는 센터코드)
     *  (.xlsx 는 엑셀에서 'CSV UTF-8' 로 저장해 올린다) */
    @PostMapping("/carrier/orders/{id}/boxes")
    @PreAuthorize("hasRole('CARRIER')")
    @Transactional
    public Map<String, Object> upload(@PathVariable("id") Long id, @RequestParam("file") MultipartFile file) throws Exception {
        Map<String, Object> o = ownOrder(id);
        List<Map<String, Object>> centers = jdbc.queryForList("SELECT center_id, center_code, center_name FROM logistics_centers WHERE center_type = 'DISTRIBUTION_CENTER'");
        List<String> errors = new ArrayList<>(); int ok = 0, line = 0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String s;
            while ((s = r.readLine()) != null) {
                line++; s = s.replace("\uFEFF", "").trim();
                if (s.isEmpty() || (line == 1 && s.toLowerCase().startsWith("box_id"))) continue;
                String[] c = s.split(",", -1);
                if (c.length < 5) { errors.add(line + "행: 열이 5개가 아닙니다"); continue; }
                String dest = c[4].trim();
                Long center = centers.stream().filter(x -> String.valueOf(x.get("center_code")).equalsIgnoreCase(dest) || String.valueOf(x.get("center_name")).startsWith(dest))
                        .map(x -> num(x.get("center_id"))).findFirst().orElse(null);
                if (center == null) { errors.add(line + "행: 목적지 센터 '" + dest + "' 없음"); continue; }
                try {
                    String box = c[0].trim(); String track = o.get("container_no") + "-" + box;
                    jdbc.update("INSERT INTO items(tracking_no, barcode, shipment_id, container_no, item_name, quantity, weight_kg, destination_center_id, status) VALUES (?,?,?,?,?,?,?,?, 'IN_CONTAINER')",
                            track, track, o.get("shipment_id"), o.get("container_no"), c[1].trim(), Integer.parseInt(c[2].trim()), new java.math.BigDecimal(c[3].trim().isEmpty() ? "0" : c[3].trim()), center);
                    ok++;
                } catch (Exception e) { errors.add(line + "행: " + (e.getMessage() != null && e.getMessage().contains("duplicate") ? "이미 등록된 박스" : "값 오류")); }
            }
        }
        if (ok == 0 && !errors.isEmpty()) throw new IllegalArgumentException("반영된 박스가 없습니다: " + String.join(" / ", errors.subList(0, Math.min(5, errors.size()))));
        return Map.of("fileName", String.valueOf(file.getOriginalFilename()), "saved", ok, "errors", errors);
    }

    // ------------------------------------------------------------ W-08 지선 배차 관리
    @GetMapping("/branch")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','HUB_OP','CARRIER')")
    public List<Map<String, Object>> branch() {
        Account a = me(); Long co = carrierCompany(a);
        return jdbc.queryForList("""
            SELECT l.assignment_id AS "tripId", c.center_name AS "dest", d.user_name AS "driverName", l.vehicle_no AS "vehicleNo", l.status, l.planned_units AS "planned",
                   (SELECT count(*) FROM branch_units u WHERE u.assignment_id = l.assignment_id AND u.status <> 'REJECTED') AS "units",
                   l.planned_depart_at AS "plannedDepartAt", (SELECT k.dock_code FROM hub_dispatches hd JOIN hub_docks k ON k.dock_id = hd.dock_id WHERE hd.assignment_id = l.assignment_id LIMIT 1) AS "outDock"
              FROM shipment_legs l JOIN logistics_centers c ON c.center_id = l.to_center_id LEFT JOIN accounts d ON d.account_id = l.driver_account_id
             WHERE l.leg_type = 'HUB_TO_CENTER' AND l.status <> 'CANCELED' AND l.received_at IS NULL
               AND (CAST(? AS bigint) IS NULL OR l.vehicle_no IN (SELECT vehicle_no FROM trucks WHERE company_id = ?))
               AND (CAST(? AS bigint) IS NULL OR l.from_center_id = ?) ORDER BY l.assignment_id DESC""", co, co, a.getUserType() == Account.UserType.HUB_OP ? a.getScopeCenterId() : null, a.getUserType() == Account.UserType.HUB_OP ? a.getScopeCenterId() : null);
    }

    /** 배차 확정 뒤 출고 도크·출발 예정 시각 (운송사). 운행 생성 자체는 POST /api/web/carrier/branch */
    @PutMapping("/carrier/branch/{id}/plan")
    @PreAuthorize("hasRole('CARRIER')")
    @Transactional
    public Map<String, Object> plan(@PathVariable("id") Long id, @RequestBody Map<String, Object> b) {
        Long co = me().getCompanyId();
        Integer own = jdbc.queryForObject("SELECT count(*) FROM shipment_legs l JOIN trucks t ON t.vehicle_no = l.vehicle_no WHERE l.assignment_id = ? AND t.company_id = ?", Integer.class, id, co);
        if (own == 0) throw new AccessDeniedException("다른 운송사의 운행입니다.");
        String at = str(b.get("plannedDepartAt"));
        if (at != null && !at.isBlank()) jdbc.update("UPDATE shipment_legs SET planned_depart_at = ?::timestamptz WHERE assignment_id = ?", at.length() == 16 ? at + ":00+09:00" : at, id);
        Long dock = num(b.get("dockId"));
        if (dock != null) hub.linkOutDock(id, dock);
        return Map.of("tripId", id, "ok", true);
    }

    // ------------------------------------------------------------ W-10 기사 가입 승인
    @GetMapping("/driver-approvals")
    @PreAuthorize("hasAnyRole('ADMIN','CARRIER')")
    public List<Map<String, Object>> approvals(@RequestParam(name = "status", defaultValue = "PENDING") String status) {
        Long co = carrierCompany(me());
        return jdbc.queryForList("""
            SELECT a.account_id AS "accountId", a.user_name AS "userName", a.user_id AS "userId", a.user_type AS "userType", c.company_name AS "companyName",
                   t.vehicle_no AS "vehicleNo", t.truck_type AS "truckType", a.license_no AS "licenseNo", a.created_at AS "createdAt", a.approval_status AS "status", a.reject_reason AS "rejectReason"
              FROM accounts a LEFT JOIN companies c ON c.company_id = a.company_id LEFT JOIN trucks t ON t.assigned_account_id = a.account_id
             WHERE a.user_type IN ('CONTAINER_DRIVER','BRANCH_DRIVER') AND a.approval_status = ? AND (CAST(? AS bigint) IS NULL OR a.company_id = ?) ORDER BY a.created_at DESC""", status, co, co);
    }

    @PostMapping("/driver-approvals/{id}/{action}")
    @PreAuthorize("hasAnyRole('ADMIN','CARRIER')")
    @Transactional
    public Map<String, Object> decide(@PathVariable("id") Long id, @PathVariable("action") String action, @RequestBody(required = false) Map<String, String> b) {
        Account a = me(); Long co = carrierCompany(a);
        if (!List.of("approve", "reject").contains(action)) throw new IllegalArgumentException("approve 또는 reject");
        List<Map<String, Object>> dl = jdbc.queryForList("SELECT company_id, user_type, approval_status FROM accounts WHERE account_id = ?", id);
        if (dl.isEmpty()) throw new IllegalArgumentException("기사 계정을 찾을 수 없습니다.");
        Map<String, Object> d = dl.get(0);
        // v10: PENDING/REJECTED 는 기사만 (DB chk_accounts_approval_driver_only)
        if (!List.of("CONTAINER_DRIVER", "BRANCH_DRIVER").contains(String.valueOf(d.get("user_type")))) throw new IllegalArgumentException("기사 계정이 아닙니다.");
        // v10: 승인자 = ADMIN 또는 기사와 같은 회사의 CORPORATE_APPROVED (DB 트리거가 23514 를 내기 전에 403)
        boolean admin = a.getUserType() == Account.UserType.ADMIN;
        boolean sameCarrier = a.getUserType() == Account.UserType.CORPORATE_APPROVED && a.getCompanyId() != null && Objects.equals(a.getCompanyId(), num(d.get("company_id")));
        if (!admin && !sameCarrier) throw new AccessDeniedException("기사 승인은 총관리자 또는 같은 운송사 담당자만 할 수 있습니다.");
        // fix_1027: 이미 처리된 신청은 다시 처리하지 않음 (반려 → 재심사 승인만 허용)
        String cur = String.valueOf(d.get("approval_status"));
        if (action.equals("reject") && !"PENDING".equals(cur)) throw new IllegalStateException("승인 대기 중인 신청만 반려할 수 있습니다. (현재: " + cur + ")");
        if (action.equals("approve") && "APPROVED".equals(cur)) throw new IllegalStateException("이미 승인된 기사입니다.");
        // v10: 반려 사유 필수 (DB chk_accounts_reject_reason)
        String reason = b == null || b.get("reason") == null ? null : b.get("reason").trim();
        if (action.equals("reject") && (reason == null || reason.isEmpty())) throw new IllegalArgumentException("반려 사유를 입력하세요.");
        if (co != null && !Objects.equals(num(d.get("company_id")), co)) throw new AccessDeniedException("소속 기사만 승인할 수 있습니다.");
        // 기획: 운송사에 승인 계정이 있으면 그 운송사가 승인, 없을 때만 ADMIN
        if (co == null) {
            Integer carrierContacts = jdbc.queryForObject("SELECT count(*) FROM v_carrier_contacts WHERE company_id = ? AND can_use_carrier_screens", Integer.class, d.get("company_id"));
            if (carrierContacts != null && carrierContacts > 0 && !Boolean.TRUE.equals(b != null && "true".equals(b.get("force"))))
                throw new IllegalStateException("이 운송사에는 승인 담당자가 있어 운송사에서 승인합니다. (총관리자 대리 승인은 force=true)");
        }
        String st = action.equals("approve") ? "APPROVED" : "REJECTED";
        jdbc.update("UPDATE accounts SET approval_status = ?, approved_at = now(), approved_by_account_id = ?, reject_reason = ? WHERE account_id = ?",
                st, a.getAccountId(), action.equals("reject") ? reason : null, id);
        if (st.equals("APPROVED")) jdbc.update("UPDATE trucks SET entry_approval = 'APPROVED' WHERE assigned_account_id = ? AND entry_approval IN ('PENDING','REJECTED')", id);
        else jdbc.update("UPDATE trucks SET entry_approval = 'REJECTED' WHERE assigned_account_id = ? AND entry_approval = 'PENDING'", id); // fix_1027
        // fix_1027: 처리된 가입 요청 알림은 운송사·총관리자 모두 읽음 처리 (종 숫자에 계속 남지 않게)
        jdbc.update("UPDATE notifications SET is_read = true, read_at = now() WHERE notification_type = 'DRIVER_APPROVAL_REQUEST' AND reference_id = ? AND NOT is_read", id);
        jdbc.update("INSERT INTO notifications(account_id, title, message, notification_type, reference_id) VALUES (?,?,?,?,?)",
                id, st.equals("APPROVED") ? "가입이 승인 되었습니다." : "가입 반려", st.equals("APPROVED") ? "가입이 승인 되었습니다." : "가입이 반려되었습니다. 사유: " + reason, "SYSTEM", id); // 26.10.28: 승인 알림은 이 문구만
        return Map.of("accountId", id, "status", st);
    }

    // ------------------------------------------------------------ W-11 기준정보
    @GetMapping("/settings")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','HUB_OP','CENTER_OP','CARRIER')")
    public List<Map<String, Object>> settings() {
        return jdbc.queryForList("SELECT setting_key AS \"key\", setting_label AS \"label\", setting_value AS \"value\", unit FROM ops_settings ORDER BY sort_order");
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public List<Map<String, Object>> saveSettings(@RequestBody Map<String, Integer> b) {
        b.forEach((k, v) -> { if (v == null || v < 0) throw new IllegalArgumentException(k + " 값은 0 이상");
            if ("RETURN_DUE_DEFAULT_DAYS".equals(k) && (v < 1 || v > 60)) throw new IllegalArgumentException("반납 기한 기본값은 1~60일"); // fix_1330
            jdbc.update("UPDATE ops_settings SET setting_value = ?, updated_by = ?, updated_at = now() WHERE setting_key = ?", v, CurrentAccount.id(), k); });
        return settings();
    }

    // ------------------------------------------------------------ W-12 톨게이트 (최근 통과·계량)
    @GetMapping("/toll/latest")
    @PreAuthorize(W03)
    public Map<String, Object> tollLatest() {
        Map<String, Object> m = new LinkedHashMap<>();
        List<Map<String, Object>> p = jdbc.queryForList("""
            SELECT tp.recognized_plate_no AS "plate", tp.vehicle_no AS "vehicleNo", g.tollgate_name AS "tollgate", tp.fare_won AS "fare", tp.passage_status AS "status", tp.detected_at AS "at", tp.check_id AS "checkId",
                   (tp.front_image_url LIKE 'seed:%') AS "demoFare"  -- v13: 시연 데이터 요금은 요금표 계산값이 아님 → 화면에 '데모 금액' 표시
              FROM toll_passages tp LEFT JOIN tollgates g ON g.tollgate_id = tp.tollgate_id ORDER BY tp.detected_at DESC NULLS LAST LIMIT 1""");
        m.put("passage", p.isEmpty() ? null : p.get(0));
        List<Map<String, Object>> c = jdbc.queryForList("""
            SELECT o.vehicle_no AS "vehicleNo", o.total_weight_kg AS "totalKg", o.is_violation AS "violation", o.vehicle_axle_count AS "axles", o.fine_manwon AS "fineManwon", o.checked_at AS "at", w.station_name AS "station"
              FROM overload_checks o LEFT JOIN weigh_stations w ON w.station_id = o.station_id
             WHERE (CAST(? AS bigint) IS NULL OR o.check_id = ?) ORDER BY o.checked_at DESC NULLS LAST LIMIT 1""", p.isEmpty() ? null : p.get(0).get("checkId"), p.isEmpty() ? null : p.get(0).get("checkId"));
        m.put("check", c.isEmpty() ? null : c.get(0));
        m.put("rules", tollFees());
        return m;
    }

    // ------------------------------------------------------------ W-13 검문소 재측정
    @GetMapping("/checkpoints/rechecks")
    @PreAuthorize(W03)
    public List<Map<String, Object>> rechecks() {
        return jdbc.queryForList("""
            SELECT x.vehicle_no AS "vehicleNo", x.station AS "station", f.total_weight_kg AS "firstKg", f.is_violation AS "firstViolation",
                   CASE WHEN x.n > 1 THEN l.total_weight_kg END AS "reKg", CASE WHEN x.n > 1 THEN l.is_violation END AS "reViolation", l.is_passed AS "passed", l.fine_manwon AS "fineManwon", x.last_at AS "at"
              FROM (SELECT vehicle_no, COALESCE(w.station_name, o.station_code, '계량소') AS station, checked_at::date AS d, count(*) AS n, min(check_id) AS first_id, max(check_id) AS last_id, max(checked_at) AS last_at
                      FROM overload_checks o LEFT JOIN weigh_stations w ON w.station_id = o.station_id GROUP BY vehicle_no, COALESCE(w.station_name, o.station_code, '계량소'), checked_at::date) x
              JOIN overload_checks f ON f.check_id = x.first_id JOIN overload_checks l ON l.check_id = x.last_id
             ORDER BY x.last_at DESC NULLS LAST LIMIT 50""");
    }

    // ------------------------------------------------------------ W-14 고속도로 요금
    @GetMapping("/toll-fees")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','CARRIER')")
    public List<Map<String, Object>> tollFees() {
        return jdbc.queryForList("SELECT vehicle_class AS \"cls\", class_name AS \"name\", axle_desc AS \"axles\", base_won AS \"base\", per_km_won AS \"perKm\", is_virtual AS \"virtual\" FROM toll_fee_rules ORDER BY vehicle_class");
    }

    @PutMapping("/toll-fees/{cls}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> saveTollFee(@PathVariable("cls") int cls, @RequestBody Map<String, Object> b) {
        jdbc.update("UPDATE toll_fee_rules SET per_km_won = ?, updated_at = now() WHERE vehicle_class = ?", new java.math.BigDecimal(String.valueOf(b.get("perKm"))), cls);
        return tollFees();
    }

    // ------------------------------------------------------------ fix_1600: 톨게이트 통과 시뮬레이션 → toll_passages 저장
    /** 운행 중 차량(운송오더·지선 운행) + 톨게이트 + 요금표 */
    @GetMapping("/toll/sim-options")
    @PreAuthorize(W03)
    public Map<String, Object> tollSimOptions() {
        return Map.of(
            "vehicles", jdbc.queryForList("""
                SELECT o.vehicle_no AS "vehicleNo", o.assignment_id AS "assignmentId", o.order_no AS "runNo", a.user_name AS "driverName", '운송오더' AS "kind"
                  FROM transport_orders o LEFT JOIN accounts a ON a.account_id = o.driver_account_id
                 WHERE o.vehicle_no IS NOT NULL AND o.status NOT IN ('HANDED_OVER','CANCELED')
                UNION ALL
                SELECT l.vehicle_no, l.assignment_id, 'LH#' || l.assignment_id, a.user_name, '지선 운행'
                  FROM shipment_legs l LEFT JOIN accounts a ON a.account_id = l.driver_account_id
                 WHERE l.vehicle_no IS NOT NULL AND l.status IN ('ASSIGNED','IN_TRANSIT') AND NOT EXISTS (SELECT 1 FROM transport_orders t WHERE t.assignment_id = l.assignment_id)
                 ORDER BY 1"""),
            "tollgates", jdbc.queryForList("SELECT tollgate_id AS \"id\", tollgate_name AS \"name\" FROM tollgates ORDER BY tollgate_id"),
            "rules", jdbc.queryForList("SELECT vehicle_class AS \"cls\", class_name AS \"name\", axle_desc AS \"axles\", base_won AS \"base\", per_km_won AS \"perKm\" FROM toll_fee_rules ORDER BY vehicle_class"));
    }

    /** 통과 확정: 요금 = 기본 + km × km당 (10원 단위 반올림). simKey 같은 요청은 한 번만 저장 */
    @PostMapping("/toll/passages")
    @PreAuthorize(W03)
    @Transactional
    public Map<String, Object> tollPass(@RequestBody Map<String, Object> b) {
        String veh = b.get("vehicleNo") == null ? "" : String.valueOf(b.get("vehicleNo")).trim();
        if (veh.isEmpty()) throw new IllegalArgumentException("차량번호를 선택하세요.");
        Long gate = staffNum(b.get("tollgateId")); if (gate == null) throw new IllegalArgumentException("톨게이트를 선택하세요.");
        int cls = b.get("cls") == null ? 5 : Integer.parseInt(String.valueOf(b.get("cls")));
        double km = b.get("km") == null ? 0 : Double.parseDouble(String.valueOf(b.get("km")));
        if (km < 0) throw new IllegalArgumentException("주행 거리는 0 이상이어야 합니다.");
        String key = b.get("simKey") == null ? null : "sim:" + String.valueOf(b.get("simKey")).trim();
        if (key != null) {
            List<Map<String, Object>> dup = jdbc.queryForList("SELECT passage_id AS \"id\", fare_won AS \"fare\", assignment_id AS \"assignmentId\" FROM toll_passages WHERE front_image_url = ?", key);
            if (!dup.isEmpty()) { Map<String, Object> m = new LinkedHashMap<>(dup.get(0)); m.put("duplicate", true); return m; }
        }
        Map<String, Object> r = jdbc.queryForList("SELECT base_won AS \"base\", per_km_won AS \"perKm\" FROM toll_fee_rules WHERE vehicle_class = ?", cls).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("요금표에 없는 차종입니다: " + cls));
        long fare = Math.round((((Number) r.get("base")).doubleValue() + ((Number) r.get("perKm")).doubleValue() * km) / 10.0) * 10;
        // 차량의 현재 운행 (운송오더 우선, 없으면 지선 운행)
        Long asg = jdbc.queryForList("""
            SELECT assignment_id FROM (
              SELECT assignment_id, 1 AS p, assigned_at AS t FROM transport_orders WHERE vehicle_no = ? AND assignment_id IS NOT NULL AND status NOT IN ('HANDED_OVER','CANCELED')
              UNION ALL
              SELECT assignment_id, 2, created_at FROM shipment_legs WHERE vehicle_no = ? AND status IN ('ASSIGNED','IN_TRANSIT')) x
             ORDER BY p, t DESC NULLS LAST LIMIT 1""", Long.class, veh, veh).stream().findFirst().orElse(null);
        Long id = jdbc.queryForObject("""
            INSERT INTO toll_passages (tollgate_id, recognized_plate_no, vehicle_no, assignment_id, recognition_status, passage_status, detected_at, passed_at, fare_won, front_image_url, plate_confidence)
            VALUES (?, ?, ?, ?, 'SUCCESS', 'PASSED', now(), now(), ?, ?, 1.0) RETURNING passage_id""", Long.class, gate, veh, veh, asg, fare, key);
        Map<String, Object> m = new LinkedHashMap<>(); m.put("id", id); m.put("fare", fare); m.put("assignmentId", asg); m.put("duplicate", false);
        return m;
    }

    // ------------------------------------------------------------ W-15 차량 점검표 (1차 vehicle_checklist)
    @GetMapping("/checklists")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','CARRIER')")
    public List<Map<String, Object>> checklists() {
        Long co = carrierCompany(me());
        return jdbc.queryForList("""
            SELECT v.inspection_id AS "id", v.vehicle_no AS "vehicleNo", v.inspection_date AS "date", a.user_name AS "inspector", v.overall_status AS "overall",
                   v.brake_status AS "brake", v.tire_status AS "tire", v.light_status AS "light", v.cargo_securement_status AS "securement", v.memo
              FROM vehicle_checklist v LEFT JOIN accounts a ON a.account_id = v.inspector_account_id LEFT JOIN trucks t ON t.vehicle_no = v.vehicle_no
             WHERE (CAST(? AS bigint) IS NULL OR t.company_id = ?) ORDER BY v.inspection_date DESC, v.inspection_id DESC LIMIT 100""", co, co);
    }

    /** fix_1543: 컨테이너 점검표 (기사 앱 출발 전 점검) — W-15 두 번째 탭
     *  26.10.26: 같은 오더에서 그 기사가 앱에 입력한 씰 번호(T-02 인수 · 허브 인계 중 가장 최근)를 컨테이너 번호 옆에 붙인다
     *   sealNo = 기사 입력 씰, registeredSealNo = 등록된(정답) 씰, sealStage = 입력 단계, sealMatch = MATCH | MISMATCH | UNREGISTERED | NONE */
    @GetMapping("/checklists/containers")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','CARRIER')")
    public List<Map<String, Object>> containerChecklists() {
        Long co = carrierCompany(me());
        return jdbc.queryForList("""
            SELECT ci.inspection_id AS "id", ci.created_at AS "at", ci.container_no AS "containerNo", o.order_no AS "orderNo", a.user_name AS "inspector",
                   ci.overall_status AS "overall", ci.items::text AS "items", ci.memo,
                   sl.seal_no AS "sealNo", COALESCE(sl.expected, rs.seal_no) AS "registeredSealNo", sl.stage AS "sealStage", sl.at AS "sealAt",
                   CASE WHEN sl.seal_no IS NULL THEN 'NONE' WHEN sl.expected IS NULL THEN 'UNREGISTERED'
                        WHEN upper(sl.seal_no) = upper(sl.expected) THEN 'MATCH' ELSE 'MISMATCH' END AS "sealMatch"
              FROM container_inspections ci LEFT JOIN accounts a ON a.account_id = ci.driver_account_id LEFT JOIN transport_orders o ON o.order_id = ci.order_id
              LEFT JOIN LATERAL (
                    SELECT x.seal_no, x.expected, x.stage, x.at FROM (
                        SELECT e.observed_seal_no AS seal_no, e.expected_seal_no AS expected,
                               CASE WHEN e.memo LIKE '%인계%' THEN 'HANDOVER' ELSE 'PICKUP' END AS stage, e.occurred_at AS at
                          FROM seal_events e
                         WHERE e.order_id = ci.order_id AND e.event_type = 'INSPECTED' AND e.observed_seal_no IS NOT NULL
                           AND e.actor_account_id = ci.driver_account_id
                        UNION ALL
                        SELECT u.observed_seal_no, NULL, u.stage, u.occurred_at
                          FROM seal_unregistered_inputs u
                         WHERE u.order_id = ci.order_id AND u.actor_account_id = ci.driver_account_id
                    ) x ORDER BY x.at DESC LIMIT 1) sl ON true
              LEFT JOIN LATERAL (
                    SELECT s.seal_no FROM container_seals s
                     WHERE s.container_no = ci.container_no AND s.status = 'ACTIVE' ORDER BY s.seal_id DESC LIMIT 1) rs ON true
             WHERE (CAST(? AS bigint) IS NULL OR a.company_id = ?) ORDER BY ci.created_at DESC LIMIT 100""", co, co);
    }

    // ------------------------------------------------------------ W-16 직원관리
    @GetMapping("/staff")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> staff() {
        return jdbc.queryForList("""
            SELECT a.account_id AS "accountId", a.user_name AS "userName", a.user_id AS "userId", a.user_type AS "role", a.phone_num AS "phone", a.hub_id AS "hubId", a.center_id AS "centerId",
                   COALESCE(h.hub_name, c.center_name) AS "site"
              FROM accounts a LEFT JOIN hubs h ON h.hub_id = a.hub_id LEFT JOIN logistics_centers c ON c.center_id = a.center_id
             WHERE a.user_type IN ('CONTROL','HUB_OP','CENTER_OP') ORDER BY a.user_type, a.user_name""");
    }

    /** fix_1434: 직원(웹 관제 계정) 소속 선택지 */
    @GetMapping("/staff/options")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> staffOptions() {
        return Map.of("hubs", jdbc.queryForList("SELECT hub_id AS \"id\", hub_name AS \"name\" FROM hubs ORDER BY hub_id"),
                      "centers", jdbc.queryForList("SELECT center_id AS \"id\", center_name AS \"name\" FROM logistics_centers ORDER BY center_id"));
    }

    /** fix_1434: 직원 수정 (이름·연락처·권한·소속). 권한은 CONTROL/HUB_OP/CENTER_OP 만 */
    @PutMapping("/staff/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public List<Map<String, Object>> updateStaff(@PathVariable("id") Long id, @RequestBody Map<String, Object> b) {
        String type = String.valueOf(b.getOrDefault("role", ""));
        if (!List.of("CONTROL", "HUB_OP", "CENTER_OP").contains(type)) throw new IllegalArgumentException("권한은 통합 관제사·허브 관제사·센터 관제사 중 하나여야 합니다.");
        String name = b.get("userName") == null ? "" : String.valueOf(b.get("userName")).trim();
        if (name.isEmpty()) throw new IllegalArgumentException("이름을 입력하세요.");
        Long hub = "HUB_OP".equals(type) ? staffNum(b.get("hubId")) : null, center = "CENTER_OP".equals(type) ? staffNum(b.get("centerId")) : null;
        if ("HUB_OP".equals(type) && hub == null) throw new IllegalArgumentException("허브 관제사는 소속 허브를 선택해야 합니다.");
        if ("CENTER_OP".equals(type) && center == null) throw new IllegalArgumentException("센터 관제사는 소속 센터를 선택해야 합니다.");
        Object ph = b.get("phone");
        int n = jdbc.update("UPDATE accounts SET user_name = ?, phone_num = ?, user_type = ?, hub_id = ?, center_id = ? WHERE account_id = ? AND user_type IN ('CONTROL','HUB_OP','CENTER_OP')",
                name, ph == null || String.valueOf(ph).isBlank() ? null : String.valueOf(ph).trim(), type, hub, center, id);
        if (n == 0) throw new IllegalArgumentException("수정할 직원 계정이 없습니다.");
        return staff();
    }

    /** fix_1434: 직원 삭제. 처리 이력(예외 담당·씰 확인 등)이 연결돼 있으면 DB 가 막고 안내 */
    @DeleteMapping("/staff/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public List<Map<String, Object>> deleteStaff(@PathVariable("id") Long id) {
        if (Objects.equals(id, CurrentAccount.id())) throw new IllegalArgumentException("본인 계정은 삭제할 수 없습니다.");
        try {
            jdbc.update("DELETE FROM notifications WHERE account_id = ?", id);
        } catch (org.springframework.dao.DataAccessException ignore) { /* 알림 테이블 구조가 다르면 건너뜀 */ }
        int n;
        try {
            n = jdbc.update("DELETE FROM accounts WHERE account_id = ? AND user_type IN ('CONTROL','HUB_OP','CENTER_OP')", id);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new IllegalStateException("처리 이력이 연결된 직원이라 삭제할 수 없습니다. 권한·소속 수정으로 관리하세요.");
        }
        if (n == 0) throw new IllegalArgumentException("삭제할 직원 계정이 없습니다.");
        return staff();
    }

    private static Long staffNum(Object o) { if (o == null || String.valueOf(o).isBlank() || "null".equals(String.valueOf(o))) return null; return Long.valueOf(String.valueOf(o)); }
}
