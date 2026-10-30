package com.scargo.ops.controller;

import com.scargo.app.security.CurrentAccount;
import com.scargo.app.entity.VehicleSpec;
import com.scargo.app.repository.VehicleSpecRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** 26.10.21 v9: 기사 앱 A-02 회원가입(4단계) · A-03 승인 대기 · T-03 운행·공컨 반납 */
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class V9AppController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final VehicleSpecRepository specs;
    private final com.scargo.ops.service.ReturnDueService returnDue;

    private static String s(Object o) { return o == null ? null : String.valueOf(o).trim(); }

    /** A-02 ② 소속 운송사 (등록·승인된 운송사만) — 비로그인 */
    /** fix_1434: 기사 앱 고속도로 요금표 (읽기 전용, 컨테이너·지선 기사) */
    @GetMapping("/toll-fees")
    @PreAuthorize("hasAnyRole('CONTAINER_DRIVER','BRANCH_DRIVER')")
    public List<Map<String, Object>> tollFees() {
        return jdbc.queryForList("SELECT vehicle_class AS \"cls\", class_name AS \"name\", axle_desc AS \"axles\", base_won AS \"base\", per_km_won AS \"perKm\", is_virtual AS \"virtual\" FROM toll_fee_rules ORDER BY vehicle_class");
    }

    /**
     * fix_1553: 기사 본인의 확정 통행료 (toll_passages, passage_status = PASSED, fare_won 있음)
     * 본인 매칭: ① 통과 기록의 운행(assignment_id)이 내 운송오더(transport_orders.driver_account_id) 또는 내 지선 운행(shipment_legs.driver_account_id)
     *           ② 운행 연결이 없으면 차량번호가 내 배정 차량(trucks.assigned_account_id)
     */
    @GetMapping("/toll-fees/mine")
    @PreAuthorize("hasAnyRole('CONTAINER_DRIVER','BRANCH_DRIVER')")
    public Map<String, Object> myTolls(@RequestParam(name = "month", required = false) String month) {
        Long me = com.scargo.app.security.CurrentAccount.id();
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT tp.passage_id AS "id", COALESCE(tp.passed_at, tp.detected_at) AS "at", tp.vehicle_no AS "vehicleNo", tp.fare_won AS "fare",
                   COALESCE(o.order_no, CASE WHEN l.assignment_id IS NOT NULL THEN 'LH#' || l.assignment_id END) AS "runNo"
              FROM toll_passages tp
              LEFT JOIN transport_orders o ON o.assignment_id = tp.assignment_id AND tp.assignment_id IS NOT NULL
              LEFT JOIN shipment_legs l ON l.assignment_id = tp.assignment_id
             WHERE tp.passage_status = 'PASSED' AND tp.fare_won IS NOT NULL
               AND ( (tp.assignment_id IS NOT NULL AND (o.driver_account_id = ? OR l.driver_account_id = ?))
                  OR (tp.assignment_id IS NULL AND tp.vehicle_no IN (SELECT vehicle_no FROM trucks WHERE assigned_account_id = ?)) )
             ORDER BY COALESCE(tp.passed_at, tp.detected_at) DESC LIMIT 500""", me, me, me);
        java.time.YearMonth ym = month == null || month.isBlank() ? java.time.YearMonth.now(java.time.ZoneId.of("Asia/Seoul")) : java.time.YearMonth.parse(month);
        long total = 0; int cnt = 0;
        for (Map<String, Object> r : rows) {
            Object at = r.get("at");
            java.time.LocalDate d = at instanceof java.sql.Timestamp ts ? ts.toInstant().atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate()
                    : at instanceof java.time.OffsetDateTime odt ? odt.atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")).toLocalDate() : null;
            if (d != null && java.time.YearMonth.from(d).equals(ym)) { total += ((Number) r.get("fare")).longValue(); cnt++; }
        }
        return Map.of("month", ym.toString(), "monthTotal", total, "monthCount", cnt, "items", rows);
    }

    @GetMapping("/signup/carriers")
    public List<Map<String, Object>> carriers() {
        return jdbc.queryForList("SELECT company_id AS \"companyId\", company_name AS \"name\" FROM companies WHERE is_carrier AND approval_status = 'APPROVED' ORDER BY company_name");
    }

    /** A-02 가입 신청 — 비로그인. body: kind(CONTAINER_DRIVER|BRANCH_DRIVER), userName, phoneNum, userId, password, companyId, vehicleNo, truckType, axleCount, licenseNo, agreeService, agreePrivacy, agreeLocation
     *  → accounts(approval_status=PENDING) + trucks(entry_approval=PENDING), 운송사에 승인 요청 알림 */
    @PostMapping("/signup")
    @Transactional
    public Map<String, Object> signup(@RequestBody Map<String, Object> b) {
        String kind = s(b.get("kind"));
        // 컨테이너 기사: 섀시(트레일러) 번호가 있으면 CONTAINER_TRUCK 으로 등록 (DB 제약: 컨테이너 특수화물차 = 세미트레일러 + 트레일러 번호)
        String trailer = b.get("trailerNo") == null || s(b.get("trailerNo")).isBlank() || "BRANCH_DRIVER".equals(s(b.get("kind"))) ? null : s(b.get("trailerNo")).trim();
        if (!List.of("CONTAINER_DRIVER", "BRANCH_DRIVER").contains(kind)) throw new IllegalArgumentException("운송 종류를 선택하세요.");
        String name = s(b.get("userName")), phone = s(b.get("phoneNum")), uid = s(b.get("userId")), pw = s(b.get("password")), vehicle = s(b.get("vehicleNo"));
        if (name == null || name.isEmpty() || uid == null || uid.length() < 4 || pw == null || pw.length() < 8) throw new IllegalArgumentException("이름·아이디(4자 이상)·비밀번호(8자 이상)를 입력하세요.");
        if (phone == null || !phone.replace("-", "").matches("01\\d{9}")) throw new IllegalArgumentException("휴대폰 번호는 010 포함 숫자 11자리로 입력하세요.");
        phone = phone.replace("-", "");
        if (vehicle == null || vehicle.isEmpty()) throw new IllegalArgumentException("차량번호를 입력하세요.");
        if (vehicle.length() > 10) throw new IllegalArgumentException("차량번호는 지역번호 포함 10자까지입니다.");
        String license = s(b.get("licenseNo"));
        if (license == null || !license.matches("\\d{2}-\\d{2}-\\d{6}")) throw new IllegalArgumentException("자격증 번호는 00-00-000000 형식으로 입력하세요.");
        if (!Boolean.TRUE.equals(b.get("agreeService")) || !Boolean.TRUE.equals(b.get("agreePrivacy")) || !Boolean.TRUE.equals(b.get("agreeLocation"))) throw new IllegalArgumentException("필수 약관에 동의하세요.");
        Long company = b.get("companyId") == null ? null : Long.valueOf(String.valueOf(b.get("companyId")));
        Integer okC = jdbc.queryForObject("SELECT count(*) FROM companies WHERE company_id = ? AND is_carrier AND approval_status = 'APPROVED'", Integer.class, company);
        if (okC == 0) throw new IllegalArgumentException("등록된 운송사를 선택하세요.");
        if (jdbc.queryForObject("SELECT count(*) FROM accounts WHERE user_id = ?", Integer.class, uid) > 0) throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        List<Map<String, Object>> truck = jdbc.queryForList("SELECT company_id, assigned_account_id FROM trucks WHERE vehicle_no = ?", vehicle);
        // fix_1027: 반려된 기사에게 배정돼 있던 차량은 다시 가입 신청할 수 있게 허용
        boolean prevRejected = !truck.isEmpty() && truck.get(0).get("assigned_account_id") != null
                && jdbc.queryForObject("SELECT count(*) FROM accounts WHERE account_id = ? AND approval_status = 'REJECTED'", Integer.class, truck.get(0).get("assigned_account_id")) > 0;
        if (!truck.isEmpty() && (!Objects.equals(((Number) truck.get(0).get("company_id")).longValue(), company) || (truck.get(0).get("assigned_account_id") != null && !prevRejected)))
            throw new IllegalArgumentException("이미 다른 기사·운송사에 등록된 차량입니다.");
        Long id = jdbc.queryForObject("INSERT INTO accounts(user_name, user_id, user_pw, user_type, company_id, phone_num, approval_status, license_no) VALUES (?,?,?,?,?,?, 'PENDING', ?) RETURNING account_id",
                Long.class, name, uid, encoder.encode(pw), kind, company, phone, license);
        Integer axles = b.get("axleCount") == null ? null : Integer.valueOf(String.valueOf(b.get("axleCount")).replaceAll("\\D.*", ""));
        if (truck.isEmpty())
            jdbc.update("INSERT INTO trucks(vehicle_no, company_id, truck_type, status, assigned_account_id, entry_approval, vehicle_category, is_semi_trailer, trailer_no) VALUES (?,?,?, 'OUTSIDE', ?, 'PENDING', ?, ?, ?)", vehicle, company, s(b.get("truckType")), id, "BRANCH_DRIVER".equals(kind) ? "GENERAL_TRUCK" : (trailer == null ? null : "CONTAINER_TRUCK"), trailer != null, trailer);
        else jdbc.update("UPDATE trucks SET assigned_account_id = ?, entry_approval = CASE WHEN entry_approval = 'REJECTED' THEN 'PENDING' ELSE entry_approval END WHERE vehicle_no = ?", id, vehicle);
        // v10: 축 수는 차량 제원(vehicle_specs.axle_count, trucks 1:1) — trucks 에 중복 컬럼 없음
        if (axles != null) {
            VehicleSpec spec = specs.findById(vehicle).orElseGet(() -> { VehicleSpec v = new VehicleSpec(); v.setVehicleNo(vehicle); return v; });
            spec.setAxleCount(axles); specs.save(spec);
        }
        jdbc.update("INSERT INTO notifications(company_id, title, message, notification_type, reference_id) VALUES (?,?,?, 'DRIVER_APPROVAL_REQUEST', ?)",
                company, "기사 가입 승인 요청", name + " (" + (kind.equals("CONTAINER_DRIVER") ? "컨테이너 운송기사" : "지선 운송기사") + ", " + vehicle + ")", id);
        // fix_1027: 총관리자에게도 알림 (운송사 승인 담당자가 없으면 총관리자가 직접 승인, 있으면 대리 승인 가능)
        Integer contacts = jdbc.queryForObject("SELECT count(*) FROM v_carrier_contacts WHERE company_id = ? AND can_use_carrier_screens", Integer.class, company);
        String coName = jdbc.queryForObject("SELECT company_name FROM companies WHERE company_id = ?", String.class, company);
        String adminMsg = name + " (" + (kind.equals("CONTAINER_DRIVER") ? "컨테이너 운송기사" : "지선 운송기사") + ", " + vehicle + ", " + coName + ")"
                + (contacts != null && contacts > 0 ? " · 운송사 승인 대기" : " · 운송사 승인 담당자가 없어 총관리자 승인 필요");
        jdbc.update("""
            INSERT INTO notifications(account_id, title, message, notification_type, reference_id)
            SELECT account_id, '기사 가입 승인 요청', ?, 'DRIVER_APPROVAL_REQUEST', ? FROM accounts WHERE user_type = 'ADMIN'""", adminMsg, id);
        return Map.of("accountId", id, "approvalStatus", "PENDING");
    }

    /** v11: 나에게 지시된 공컨 반납 (T-03). 반납 중 CY 변경은 확인 대기 목록 포함 */
    @GetMapping("/container/returns")
    @PreAuthorize("hasRole('CONTAINER_DRIVER')")
    public List<Map<String, Object>> myReturns() {
        List<Map<String, Object>> l = jdbc.queryForList("""
            SELECT o.order_id AS "orderId", o.order_no AS "orderNo", o.container_no AS "containerNo", o.status, o.return_status AS "returnStatus",
                   o.return_cy_id AS "returnCyId", o.return_cy AS "returnCy", cy.address AS "returnCyAddress", o.return_due_at AS "returnDueAt",
                   o.return_ready_at AS "returnReadyAt", o.return_instructed_at AS "instructedAt", o.return_started_at AS "returnStartedAt",
                   o.return_arrived_at AS "returnArrivedAt", o.empty_returned_at AS "emptyReturnedAt", h.hub_name AS "hubName",
                   o.damage_noted AS "damageNoted", CASE WHEN o.damage_noted THEN 'CY 게이트에서 손상 확인서 필요' END AS "damageNotice",
                   (SELECT i.damage_location || ' · ' || i.damage_type FROM empty_container_inspections i WHERE i.order_id = o.order_id AND i.result = 'DAMAGED') AS "damageInfo"
              FROM transport_orders o LEFT JOIN return_cys cy ON cy.cy_id = o.return_cy_id LEFT JOIN hubs h ON h.hub_id = o.dest_hub_id
             WHERE o.return_driver_account_id = ? AND o.return_status IS DISTINCT FROM 'EMPTY_RETURNED' AND o.status <> 'CANCELED' ORDER BY o.return_due_at NULLS LAST, o.order_id""", CurrentAccount.id());
        for (Map<String, Object> m : l)
            m.put("attachments", V9WebController.attachments(jdbc, ((Number) m.get("orderId")).longValue()));
        for (Map<String, Object> m : l)
            m.put("pendingCyChanges", jdbc.queryForList("SELECT change_id AS \"changeId\", old_value AS \"oldValue\", new_value AS \"newValue\", reason, changed_at AS \"changedAt\" FROM return_plan_changes WHERE order_id = ? AND driver_account_id = ? AND driver_confirmed_at IS NULL ORDER BY change_id", m.get("orderId"), CurrentAccount.id()));
        return l;
    }

    /** v11: 반납 중 반납지(CY) 변경 확인 → RETURN_CY_CHANGED 예외 해결 */
    @PostMapping("/container/cy-changes/{changeId}/confirm")
    @PreAuthorize("hasRole('CONTAINER_DRIVER')")
    @Transactional
    public Map<String, Object> confirmCyChange(@PathVariable("changeId") Long changeId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT driver_account_id, driver_confirmed_at FROM return_plan_changes WHERE change_id = ?", changeId);
        if (l.isEmpty()) throw new IllegalArgumentException("변경 이력이 없습니다.");
        Object d = l.get(0).get("driver_account_id");
        if (d == null || ((Number) d).longValue() != CurrentAccount.id()) throw new AccessDeniedException("내가 확인할 변경이 아닙니다.");
        jdbc.update("UPDATE return_plan_changes SET driver_confirmed_at = COALESCE(driver_confirmed_at, now()) WHERE change_id = ?", changeId);
        jdbc.update("UPDATE exceptions SET status = 'RESOLVED', resolved_at = now(), resolution_note = '기사 반납지 변경 확인' WHERE exception_type = 'RETURN_CY_CHANGED' AND source_table = 'return_plan_changes' AND source_key = ? AND status <> 'RESOLVED'", String.valueOf(changeId));
        return Map.of("changeId", changeId, "confirmed", true);
    }

    /** v12: 반납 첨부 업로드 (반납 기사). 반납 기사 여부는 DB 가 판정 → 아니면 409 */
    @PostMapping("/container/orders/{id}/attachments")
    @PreAuthorize("hasRole('CONTAINER_DRIVER')")
    @Transactional
    public Map<String, Object> uploadAttachment(@PathVariable("id") Long id, @RequestParam("kind") String kind, @RequestParam("file") MultipartFile file) throws Exception {
        return V9WebController.saveAttachment(jdbc, id, kind, file, CurrentAccount.id());
    }

    /** v11: 반납 CY 목록 (T-03 선택) */
    @GetMapping("/return-cys")
    public List<Map<String, Object>> returnCys() {
        return jdbc.queryForList("SELECT cy_id AS \"id\", cy_code AS \"code\", cy_name AS \"name\", address, is_demo AS \"isDemo\" FROM return_cys WHERE is_active ORDER BY sort_order, cy_id");
    }

    /** A-03 승인 대기 상태 (로그인한 본인) */
    @GetMapping("/approval")
    public Map<String, Object> approval() {
        return jdbc.queryForMap("""
            SELECT a.user_name AS "userName", a.user_type AS "userType", a.approval_status AS "status", a.reject_reason AS "rejectReason", a.created_at AS "createdAt",
                   a.approved_at AS "approvedAt", c.company_name AS "companyName" FROM accounts a LEFT JOIN companies c ON c.company_id = a.company_id WHERE a.account_id = ?""", CurrentAccount.id());
    }

    // ------------------------------------------------------------ T-03 운행 현황 · 공컨 반납
    private Map<String, Object> myOrder(Long id) {
        Map<String, Object> o = jdbc.queryForMap("SELECT * FROM transport_orders WHERE order_id = ?", id);
        Long me = CurrentAccount.id();
        if (!Objects.equals(o.get("driver_account_id") == null ? null : ((Number) o.get("driver_account_id")).longValue(), me)
            && !Objects.equals(o.get("return_driver_account_id") == null ? null : ((Number) o.get("return_driver_account_id")).longValue(), me))
            throw new AccessDeniedException("내 운송오더가 아닙니다.");
        return o;
    }

    // 26.10.29 QA: 운송 진행(T-03)은 진행 중인 오더 → 반납 중/지시된 오더 → 가장 최근에 인계한 오더 순 (예전: 가장 오래된 오더라 방금 인계한 컨테이너 대신 지난 오더가 보였음)
    @GetMapping("/container/trip")
    @PreAuthorize("hasRole('CONTAINER_DRIVER')")
    public Map<String, Object> trip() {
        List<Map<String, Object>> l = jdbc.queryForList("""
            SELECT o.order_id AS "orderId", o.order_no AS "orderNo", o.container_no AS "containerNo", o.status, o.eta, o.assigned_at AS "assignedAt",
                   (SELECT min(e.occurred_at) FROM seal_events e JOIN container_seals s ON s.seal_id = e.seal_id WHERE s.shipment_id = o.shipment_id) AS "pickedUpAt",
                   o.gate_out_at AS "gateOutAt", o.departed_at AS "departedAt", o.arrived_at AS "arrivedAt", o.handed_over_at AS "handedOverAt",
                   o.return_status AS "returnStatus", o.return_cy AS "returnCy", o.return_due_at AS "returnDueAt", o.return_started_at AS "returnStartedAt",
                   o.return_arrived_at AS "returnArrivedAt", o.empty_returned_at AS "emptyReturnedAt", o.return_photo_url AS "returnPhoto",
                   o.return_cy_id AS "returnCyId", cy.address AS "returnCyAddress",
                   (SELECT r.unloaded_at FROM hub_inbound_receipts r WHERE r.assignment_id = o.assignment_id AND r.status = 'COMPLETED') AS "unloadedAt",
                   (SELECT i.result FROM empty_container_inspections i WHERE i.order_id = o.order_id) AS "inspection",
                   (SELECT i.damage_note FROM empty_container_inspections i WHERE i.order_id = o.order_id) AS "damageNote"
              FROM transport_orders o LEFT JOIN return_cys cy ON cy.cy_id = o.return_cy_id WHERE o.driver_account_id = ? AND o.status <> 'CANCELED' AND o.empty_returned_at IS NULL
             ORDER BY (o.status <> 'HANDED_OVER') DESC, (o.return_status IS NOT NULL) DESC, COALESCE(o.handed_over_at, o.arrived_at, o.created_at) DESC NULLS LAST, o.order_id DESC LIMIT 1""", CurrentAccount.id());
        if (l.isEmpty()) return Map.of();
        Map<String, Object> m = new LinkedHashMap<>(l.get(0));
        m.put("tolls", jdbc.queryForList("SELECT g.tollgate_name AS \"name\", tp.fare_won AS \"fare\", tp.passed_at AS \"at\" FROM toll_passages tp JOIN tollgates g ON g.tollgate_id = tp.tollgate_id JOIN transport_orders o ON o.assignment_id = tp.assignment_id WHERE o.order_id = ? ORDER BY tp.detected_at", m.get("orderId")));
        return m;
    }

    /** 공컨 반납: start(허브 하역 후 반납 운행) → arrive(반납지 도착) → done(증빙 사진, 반납 접수 확정) */
    @PostMapping("/container/orders/{id}/return/{step}")
    @PreAuthorize("hasRole('CONTAINER_DRIVER')")
    @Transactional
    public Map<String, Object> returnStep(@PathVariable("id") Long id, @PathVariable("step") String step, @RequestParam(value = "photo", required = false) MultipartFile photo,
                                          @RequestParam(value = "returnCyId", required = false) Long returnCyId,
                                          @RequestParam(value = "receiverName", required = false) String receiverName,
                                          @RequestParam(value = "eirNo", required = false) String eirNo) throws Exception {
        Map<String, Object> o = myOrder(id);
        String cur = (String) o.get("return_status");
        switch (step) {
            case "start" -> {
                // v10: 반납은 허브 인계 완료(HANDED_OVER) 오더만 (DB chk_transport_orders_return_after_handover)
                if (!"HANDED_OVER".equals(String.valueOf(o.get("status")))) throw new IllegalStateException("허브 인계 완료(HANDED_OVER) 후에 반납 운행을 시작합니다.");
                if (cur != null) throw new IllegalStateException("이미 반납 운행 중입니다.");
                // v11: 허브 하역 완료 + 공컨 상태 점검(OK) 뒤에만 (DB 트리거 trg_orders_return_start 와 같은 조건)
                Integer unloaded = jdbc.queryForObject("SELECT count(*) FROM hub_inbound_receipts WHERE assignment_id = ? AND status = 'COMPLETED' AND unloaded_at IS NOT NULL", Integer.class, o.get("assignment_id"));
                if (unloaded == null || unloaded == 0) throw new IllegalStateException("허브 하역이 끝난 뒤에 반납 운행을 시작합니다.");
                List<String> insp = jdbc.queryForList("SELECT result FROM empty_container_inspections WHERE order_id = ?", String.class, id);
                if (insp.isEmpty()) throw new IllegalStateException("허브에서 공컨 상태 점검을 마친 뒤에 반납 운행을 시작합니다.");
                // v12 (14-5 확정): 손상(DAMAGED)이어도 반납 진행. 점검 완료만 필요
                // v11: 운송사가 이 기사에게 반납을 지시한 오더만
                if (o.get("return_ready_at") == null) throw new IllegalStateException("아직 반납 가능 상태가 아닙니다 (허브 확인 대기).");
                if (o.get("return_driver_account_id") == null) throw new IllegalStateException("운송사의 반납 지시 전입니다.");
                if (((Number) o.get("return_driver_account_id")).longValue() != CurrentAccount.id()) throw new AccessDeniedException("나에게 지시된 반납이 아닙니다.");
                // v11: 반납 CY 가 정해지지 않은 오더는 기사가 목록에서 선택
                if (o.get("return_cy_id") == null) {
                    if (returnCyId == null) throw new IllegalArgumentException("반납 CY 를 목록에서 선택하세요.");
                    List<Map<String, Object>> cy = jdbc.queryForList("SELECT cy_id, cy_name FROM return_cys WHERE cy_id = ? AND is_active", returnCyId);
                    if (cy.isEmpty()) throw new IllegalArgumentException("반납 CY 목록에 없는 값입니다.");
                    jdbc.update("UPDATE transport_orders SET return_cy_id = ?, return_cy = ? WHERE order_id = ?", returnCyId, cy.get(0).get("cy_name"), id);
                }
                jdbc.update("UPDATE transport_orders SET return_status = 'RETURNING_EMPTY', return_started_at = now(), updated_at = now() WHERE order_id = ?", id);
            }
            case "arrive" -> {
                if (o.get("return_driver_account_id") == null || ((Number) o.get("return_driver_account_id")).longValue() != CurrentAccount.id()) throw new AccessDeniedException("나에게 지시된 반납이 아닙니다.");
                if (!"RETURNING_EMPTY".equals(cur)) throw new IllegalStateException("반납 운행 중이 아닙니다.");
                jdbc.update("UPDATE transport_orders SET return_status = 'RETURN_ARRIVED', return_arrived_at = now(), updated_at = now() WHERE order_id = ?", id);
            }
            case "done" -> {
                if (o.get("return_driver_account_id") == null || ((Number) o.get("return_driver_account_id")).longValue() != CurrentAccount.id()) throw new AccessDeniedException("나에게 지시된 반납이 아닙니다.");
                // v11: 반납지 도착(RETURN_ARRIVED) 을 거쳐야 완료 (DB 트리거도 직행 차단)
                if (!"RETURN_ARRIVED".equals(cur)) throw new IllegalStateException("반납지 도착을 먼저 등록하세요.");
                if (receiverName == null || receiverName.isBlank()) throw new IllegalArgumentException("CY 인수자 이름을 입력하세요.");
                if (eirNo == null || eirNo.isBlank()) throw new IllegalArgumentException("EIR 번호를 입력하세요.");
                if (photo == null || photo.isEmpty()) throw new IllegalArgumentException("반납 증빙 사진을 올려 주세요.");
                String url = com.scargo.config.UploadStorage.save("returns", id + "_" + System.currentTimeMillis(), photo); // fix_1300: 저장 위치 단일화·확장자 검사
                jdbc.update("UPDATE transport_orders SET return_status = 'EMPTY_RETURNED', empty_returned_at = now(), return_photo_url = ?, return_receiver_name = ?, return_eir_no = ?, updated_at = now() WHERE order_id = ?",
                        url, receiverName.trim(), eirNo.trim().toUpperCase(), id);
            }
            default -> throw new IllegalArgumentException("start · arrive · done");
        }
        if ("done".equals(step)) returnDue.syncExceptions(id);   // v11: 반납 완료 → 반납 기한 예외 자동 해결
        return Map.of("orderId", id, "returnStatus", jdbc.queryForObject("SELECT return_status FROM transport_orders WHERE order_id = ?", String.class, id));
    }
}
