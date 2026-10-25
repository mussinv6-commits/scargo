package com.scargo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scargo.dto.HubArrivalCandidateResponse;
import com.scargo.dto.HubArrivalCandidateResponse.OcrEvidence;
import com.scargo.dto.HubArrivalRequest;
import com.scargo.dto.HubHandoverRequest;
import com.scargo.security.HubScopePolicy;
import com.scargo.security.HubScopePolicy.HubScope;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 26.10.23 허브 도착 · 인계 기록 (기존 표만 사용: transport_orders · shipment_legs · hub_inbound_receipts · handovers · trip_events)
 *
 * 도착 확정: 오더를 잠그고 도착 허브 · 운송 구간 · 입차 OCR 기록 · 기존 입고 기록을 확인한 뒤
 *   입고 기록(ARRIVED) → 오더 ARRIVED → 운송 구간 ARRIVED → trip_events HUB_ENTER
 *   LOADED · GATE_OUT · IN_TRANSIT 단계와 그 시각은 만들지 않는다 (기록하는 기능이 아직 없음)
 *   입차 OCR 기록(hub_vehicle_logs)은 시뮬레이터도 만들기 때문에 처리자가 직접 고른 근거로만 쓴다
 * 인계 확정: 오더를 잠그고 ARRIVED · 소속 허브 입고 기록 · 인계 기록 없음 · ACTIVE 씰 번호 일치를 확인한 뒤
 *   handovers(seal_id 연결) → 오더 HANDED_OVER → trip_events HANDOVER
 *   씰이 없거나 번호가 다르면 409 (기존 씰 확인의 "보류" 규칙과 같음)
 *
 * 허브 범위는 HubScopePolicy (ADMIN 전체 · CONTROL 조회만 · HUB_OP 소속 허브)
 * 상태 · 근거 불일치는 IllegalStateException(409), 범위 밖은 AccessDeniedException(403)
 */
@Service
@RequiredArgsConstructor
public class HubArrivalService {

    // 도착 확정 대상 (CREATED 는 배차 전이라 제외)
    static final Set<String> ARRIVABLE = Set.of("ASSIGNED", "LOADED", "GATE_OUT", "IN_TRANSIT");

    private static final String ORDER_COLUMNS =
            "o.order_id, o.order_no, o.status, o.company_id, o.container_no, o.vehicle_no, o.dest_hub_id, "
            + "o.shipment_id, o.assignment_id, o.assigned_at, o.departed_at, o.arrived_at, o.handed_over_at";

    private static final String CANDIDATE_SQL = "SELECT " + ORDER_COLUMNS + ", "
            + "l.status AS leg_status, l.leg_type, "
            + "r.receipt_id, r.hub_id AS receipt_hub_id, r.status AS receipt_status, r.dock_id, r.arrived_at AS receipt_arrived_at, "
            + "h.handover_id, h.completed_at AS handover_completed_at, "
            + "EXISTS (SELECT 1 FROM container_seals s WHERE s.shipment_id = o.shipment_id "
            + "        AND s.container_no = o.container_no AND s.status = 'ACTIVE') AS seal_registered "
            + "FROM transport_orders o "
            + "LEFT JOIN shipment_legs l ON l.assignment_id = o.assignment_id "
            + "LEFT JOIN hub_inbound_receipts r ON r.assignment_id = o.assignment_id "
            + "LEFT JOIN handovers h ON h.order_id = o.order_id ";

    private final JdbcTemplate jdbc;
    private final HubScopePolicy hubScopePolicy;
    private final ObjectMapper objectMapper;

    private record OrderRow(Long orderId, String orderNo, String status, Long companyId, String containerNo,
                            String vehicleNo, Long destHubId, Long shipmentId, Long assignmentId,
                            OffsetDateTime assignedAt, OffsetDateTime departedAt, OffsetDateTime arrivedAt, OffsetDateTime handedOverAt) {
        // 도착 근거가 될 수 있는 입차 기록의 기준 시각: 출발했으면 출발 시각, 아니면 배차 시각
        OffsetDateTime evidenceSince() { return departedAt != null ? departedAt : assignedAt; }
    }

    // ── 조회 ─────────────────────────────────────────────

    /** 소속 허브로 오는 진행 중 오더 (includeHandedOver = true 면 인계 완료 오더도) */
    @Transactional(readOnly = true)
    public List<HubArrivalCandidateResponse> candidates(Authentication authentication, Long hubId, boolean includeHandedOver) {
        HubScope scope = hubScopePolicy.view(authentication);
        Long filterHub = hubId;
        if (hubId != null) {
            hubScopePolicy.requireHub(scope, hubId);
        } else if (!scope.allHubs()) {
            filterHub = scope.hubId();
        }

        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder(CANDIDATE_SQL)
                .append(includeHandedOver
                        ? "WHERE o.status IN ('ASSIGNED', 'LOADED', 'GATE_OUT', 'IN_TRANSIT', 'ARRIVED', 'HANDED_OVER') "
                        : "WHERE o.status IN ('ASSIGNED', 'LOADED', 'GATE_OUT', 'IN_TRANSIT', 'ARRIVED') ");
        if (filterHub != null) {
            sql.append("AND o.dest_hub_id = ? ");
            args.add(filterHub);
        }
        sql.append("ORDER BY o.order_id");
        return jdbc.query(sql.toString(), (rs, i) -> candidate(rs, scope), args.toArray());
    }

    /** 26.10.23 도착 확정 화면의 입고(IN) 도크 목록. occupied = 하역 전 입고 기록이 그 도크를 쓰는 중 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> inboundDocks(Authentication authentication, Long hubId) {
        HubScope scope = hubScopePolicy.view(authentication);
        Long filterHub = hubId;
        if (hubId != null) {
            hubScopePolicy.requireHub(scope, hubId);
        } else if (!scope.allHubs()) {
            filterHub = scope.hubId();
        }
        String sql = "SELECT d.dock_id AS \"dockId\", d.hub_id AS \"hubId\", d.dock_code AS \"dockCode\", d.status, "
                + "EXISTS (SELECT 1 FROM hub_inbound_receipts r WHERE r.dock_id = d.dock_id "
                + "        AND r.status IN ('ARRIVED', 'UNLOADING')) AS occupied "
                + "FROM hub_docks d WHERE d.dock_type = 'IN' " + (filterHub == null ? "" : "AND d.hub_id = ? ")
                + "ORDER BY d.hub_id, d.dock_code";
        return filterHub == null ? jdbc.queryForList(sql) : jdbc.queryForList(sql, filterHub);
    }

    // ── 하역 완료 (26.10.24) ─────────────────────────────

    /**
     * 허브 입고 기록(ARRIVED · UNLOADING)을 COMPLETED 로 바꾸고 하역 시각을 기록한다.
     * 도착 때 쓴 입고(IN) 도크는 uk_receipts_active_dock 조건에서 빠져 바로 다시 쓸 수 있다.
     * 소속 허브 범위(HUB_OP) · ADMIN 만 처리, CONTROL 은 403.
     */
    @Transactional
    public Map<String, Object> unload(Authentication authentication, Long receiptId) {
        HubScope scope = hubScopePolicy.manage(authentication);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT receipt_id, hub_id, status, dock_id, container_no FROM hub_inbound_receipts WHERE receipt_id = ? FOR UPDATE",
                receiptId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("입고 기록을 찾을 수 없습니다: " + receiptId);
        }
        Map<String, Object> row = rows.get(0);
        Long hubId = ((Number) row.get("hub_id")).longValue();
        hubScopePolicy.requireHub(scope, hubId);
        String status = String.valueOf(row.get("status"));
        if ("COMPLETED".equals(status)) {
            throw new IllegalStateException("이미 하역이 완료된 입고 기록입니다: " + receiptId);
        }
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("UPDATE hub_inbound_receipts SET status = 'COMPLETED', unloaded_at = ? WHERE receipt_id = ?", now, receiptId);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("receiptId", receiptId);
        result.put("hubId", hubId);
        result.put("status", "COMPLETED");
        result.put("dockId", row.get("dock_id"));
        result.put("containerNo", row.get("container_no"));
        result.put("unloadedAt", now.toString());
        return result;
    }

    // ── 도착 확정 ─────────────────────────────────────────

    @Transactional
    public HubArrivalCandidateResponse arrive(Authentication authentication, HubArrivalRequest request) {
        HubScope scope = hubScopePolicy.manage(authentication);
        OrderRow order = lockOrder(request.getOrderId());
        // 범위 밖 허브의 오더는 상태도 알려주지 않는다 (403 먼저)
        hubScopePolicy.requireHub(scope, order.destHubId());

        if ("ARRIVED".equals(order.status())) {
            throw new IllegalStateException("이미 허브 도착이 확정된 오더입니다: " + order.orderNo());
        }
        if (!ARRIVABLE.contains(order.status())) {
            throw new IllegalStateException("도착을 확정할 수 없는 오더 상태입니다: " + order.status());
        }
        if (order.assignmentId() == null || order.vehicleNo() == null) {
            throw new IllegalStateException("오더에 운송 구간이 없습니다: " + order.orderNo());
        }

        // 운송 구간: 항만 → 허브, 차량 일치, 취소 · 도착 아님
        List<Map<String, Object>> legs = jdbc.queryForList(
                "SELECT leg_type, vehicle_no, status FROM shipment_legs WHERE assignment_id = ? FOR UPDATE",
                order.assignmentId());
        if (legs.isEmpty()) {
            throw new IllegalStateException("오더에 운송 구간이 없습니다: " + order.orderNo());
        }
        Map<String, Object> leg = legs.get(0);
        if (!"PORT_TO_HUB".equals(leg.get("leg_type")) || !order.vehicleNo().equals(leg.get("vehicle_no"))) {
            throw new IllegalStateException("오더와 운송 구간(항만 → 허브)의 차량이 맞지 않습니다: " + order.orderNo());
        }
        if (!Set.of("ASSIGNED", "IN_TRANSIT").contains(String.valueOf(leg.get("status")))) {
            throw new IllegalStateException("운송 구간 상태가 도착을 확정할 수 없는 상태입니다: " + leg.get("status"));
        }

        // 입차 OCR 근거: 같은 허브 · 입차 · 허용/보류 · 같은 차량 · 배정 이후
        requireOcrEvidence(order, request.getHubVehicleLogId());

        Integer receipts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hub_inbound_receipts WHERE assignment_id = ?", Integer.class, order.assignmentId());
        if (receipts != null && receipts > 0) {
            throw new IllegalStateException("이미 이 운송 구간의 허브 입고 기록이 있습니다: " + order.orderNo());
        }

        if (request.getDockId() != null) {
            Integer docks = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM hub_docks WHERE dock_id = ? AND hub_id = ? AND dock_type = 'IN'",
                    Integer.class, request.getDockId(), order.destHubId());
            if (docks == null || docks == 0) {
                throw new IllegalArgumentException("도착 허브의 입고(IN) 도크가 아닙니다: " + request.getDockId());
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        try {
            jdbc.update("INSERT INTO hub_inbound_receipts (assignment_id, hub_id, dock_id, vehicle_no, container_no, status, arrived_at) "
                            + "VALUES (?, ?, ?, ?, ?, 'ARRIVED', ?)",
                    order.assignmentId(), order.destHubId(), request.getDockId(), order.vehicleNo(), order.containerNo(), now);
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("입고 도크가 사용 중이거나 이미 입고 기록이 있습니다.");
        }
        jdbc.update("UPDATE transport_orders SET status = 'ARRIVED', arrived_at = ? WHERE order_id = ?", now, order.orderId());
        jdbc.update("UPDATE shipment_legs SET status = 'ARRIVED', arrived_at = ? WHERE assignment_id = ?", now, order.assignmentId());
        jdbc.update("INSERT INTO trip_events (order_id, event_type, ref_type, ref_id, message, occurred_at) VALUES (?, 'HUB_ENTER', 'HUB', ?, ?, ?)",
                order.orderId(), String.valueOf(order.destHubId()),
                "허브 도착 확정 · 처리자 " + scope.userId() + " · 입차 OCR 기록 #" + request.getHubVehicleLogId(), now);

        return one(order.orderId(), scope);
    }

    private void requireOcrEvidence(OrderRow order, Long logId) {
        List<Map<String, Object>> logs = jdbc.queryForList(
                "SELECT hub_id, direction, decision, recognized_plate, created_at FROM hub_vehicle_logs WHERE log_id = ?", logId);
        if (logs.isEmpty()) {
            throw new IllegalStateException("입차 OCR 기록이 없습니다: " + logId);
        }
        Map<String, Object> log = logs.get(0);
        if (!order.destHubId().equals(toLong(log.get("hub_id")))) {
            throw new IllegalStateException("입차 OCR 기록이 오더의 도착 허브 기록이 아닙니다.");
        }
        if (!"ENTRY".equals(log.get("direction")) || !Set.of("ALLOW", "HOLD").contains(String.valueOf(log.get("decision")))) {
            throw new IllegalStateException("허용 · 보류된 입차 기록만 도착 근거로 쓸 수 있습니다.");
        }
        if (!order.vehicleNo().equals(normalizePlate((String) log.get("recognized_plate")))) {
            throw new IllegalStateException("입차 OCR 번호판이 오더 차량(" + order.vehicleNo() + ")과 다릅니다.");
        }
        OffsetDateTime createdAt = jdbc.queryForObject(
                "SELECT created_at FROM hub_vehicle_logs WHERE log_id = ?", OffsetDateTime.class, logId);
        if (order.evidenceSince() == null || createdAt == null || createdAt.isBefore(order.evidenceSince())) {
            throw new IllegalStateException(order.departedAt() != null
                    ? "운송 출발 이후의 입차 OCR 기록이 아닙니다." : "배차 이후의 입차 OCR 기록이 아닙니다.");
        }
    }

    // ── 인계 확정 ─────────────────────────────────────────

    @Transactional
    public HubArrivalCandidateResponse handover(Authentication authentication, HubHandoverRequest request) {
        HubScope scope = hubScopePolicy.manage(authentication);
        if (!Boolean.TRUE.equals(request.getContainerNoConfirmed())) {
            throw new IllegalArgumentException("컨테이너 번호를 확인해야 인계할 수 있습니다.");
        }
        OrderRow order = lockOrder(request.getOrderId());
        hubScopePolicy.requireHub(scope, order.destHubId());

        if (!"ARRIVED".equals(order.status())) {
            throw new IllegalStateException("허브 도착이 확정된 오더만 인계할 수 있습니다 (현재 " + order.status() + ")");
        }
        Integer receipts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hub_inbound_receipts WHERE assignment_id = ? AND hub_id = ?",
                Integer.class, order.assignmentId(), order.destHubId());
        if (receipts == null || receipts == 0) {
            throw new IllegalStateException("도착 허브의 입고 기록이 없습니다: " + order.orderNo());
        }
        Integer handovers = jdbc.queryForObject(
                "SELECT COUNT(*) FROM handovers WHERE order_id = ?", Integer.class, order.orderId());
        if (handovers != null && handovers > 0) {
            throw new IllegalStateException("이미 인계 기록이 있습니다: " + order.orderNo());
        }

        String input = request.getSealNo().replace(" ", "").trim().toUpperCase();
        List<Map<String, Object>> seals = jdbc.queryForList(
                "SELECT seal_id, shipment_id, seal_no FROM container_seals "
                        + "WHERE shipment_id = ? AND container_no = ? AND status = 'ACTIVE' ORDER BY seal_id",
                order.shipmentId(), order.containerNo());
        if (seals.isEmpty()) {
            throw new IllegalStateException("기준 씰 번호가 없습니다 · 인계 보류 (수동 확인 필요)");
        }
        Map<String, Object> seal = seals.stream()
                .filter(s -> input.equalsIgnoreCase(String.valueOf(s.get("seal_no"))))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("씰 번호가 일치하지 않습니다 · 인계 보류"));

        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("INSERT INTO handovers (order_id, container_no_confirmed, seal_no, seal_ok, damage_free, photo_urls, "
                        + "receiver_name, completed_at, seal_id, seal_shipment_id) VALUES (?, true, ?, true, ?, CAST(? AS jsonb), ?, ?, ?, ?)",
                order.orderId(), seal.get("seal_no"), request.getDamageFree(), photoJson(request.getPhotoUrls()),
                request.getReceiverName().trim(), now, seal.get("seal_id"), seal.get("shipment_id"));
        jdbc.update("UPDATE transport_orders SET status = 'HANDED_OVER', handed_over_at = ? WHERE order_id = ?", now, order.orderId());
        jdbc.update("INSERT INTO trip_events (order_id, event_type, ref_type, ref_id, message, occurred_at) VALUES (?, 'HANDOVER', 'HUB', ?, ?, ?)",
                order.orderId(), String.valueOf(order.destHubId()),
                "허브 인계 확정 · 처리자 " + scope.userId() + " · 인수자 " + request.getReceiverName().trim()
                        + (Boolean.TRUE.equals(request.getDamageFree()) ? " · 손상 없음" : " · 손상 있음"), now);

        return one(order.orderId(), scope);
    }

    // ── 공통 ─────────────────────────────────────────────

    private OrderRow lockOrder(Long orderId) {
        List<OrderRow> rows = jdbc.query("SELECT " + ORDER_COLUMNS + " FROM transport_orders o WHERE o.order_id = ? FOR UPDATE",
                (rs, i) -> new OrderRow(rs.getLong("order_id"), rs.getString("order_no"), rs.getString("status"),
                        rs.getLong("company_id"), rs.getString("container_no"), rs.getString("vehicle_no"),
                        rs.getLong("dest_hub_id"), nullableLong(rs, "shipment_id"), nullableLong(rs, "assignment_id"),
                        rs.getObject("assigned_at", OffsetDateTime.class), rs.getObject("departed_at", OffsetDateTime.class),
                        rs.getObject("arrived_at", OffsetDateTime.class),
                        rs.getObject("handed_over_at", OffsetDateTime.class)),
                orderId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "오더가 없습니다: " + orderId);
        }
        return rows.get(0);
    }

    private HubArrivalCandidateResponse one(Long orderId, HubScope scope) {
        return jdbc.query(CANDIDATE_SQL + "WHERE o.order_id = ?", (rs, i) -> candidate(rs, scope), orderId).get(0);
    }

    private HubArrivalCandidateResponse candidate(ResultSet rs, HubScope scope) throws SQLException {
        String status = rs.getString("status");
        Long destHubId = rs.getLong("dest_hub_id");
        String vehicleNo = rs.getString("vehicle_no");
        OffsetDateTime assignedAt = rs.getObject("assigned_at", OffsetDateTime.class);
        OffsetDateTime departedAt = rs.getObject("departed_at", OffsetDateTime.class);
        Long receiptId = nullableLong(rs, "receipt_id");
        Long receiptHubId = nullableLong(rs, "receipt_hub_id");
        Long handoverId = nullableLong(rs, "handover_id");
        String legStatus = rs.getString("leg_status");

        List<OcrEvidence> evidence = ARRIVABLE.contains(status) && receiptId == null
                ? ocrEvidence(destHubId, vehicleNo, departedAt != null ? departedAt : assignedAt) : List.of();
        boolean legOk = "PORT_TO_HUB".equals(rs.getString("leg_type"))
                && ("ASSIGNED".equals(legStatus) || "IN_TRANSIT".equals(legStatus));

        return HubArrivalCandidateResponse.builder()
                .orderId(rs.getLong("order_id"))
                .orderNo(rs.getString("order_no"))
                .orderStatus(status)
                .companyId(rs.getLong("company_id"))
                .containerNo(rs.getString("container_no"))
                .vehicleNo(vehicleNo)
                .destHubId(destHubId)
                .assignedAt(assignedAt)
                .arrivedAt(rs.getObject("arrived_at", OffsetDateTime.class))
                .handedOverAt(rs.getObject("handed_over_at", OffsetDateTime.class))
                .assignmentId(nullableLong(rs, "assignment_id"))
                .legStatus(legStatus)
                .receiptId(receiptId)
                .receiptStatus(rs.getString("receipt_status"))
                .dockId(nullableLong(rs, "dock_id"))
                .receiptArrivedAt(rs.getObject("receipt_arrived_at", OffsetDateTime.class))
                .handoverId(handoverId)
                .handoverCompletedAt(rs.getObject("handover_completed_at", OffsetDateTime.class))
                .sealRegistered(rs.getBoolean("seal_registered"))
                .canArrive(scope.canManage() && ARRIVABLE.contains(status) && receiptId == null && legOk && !evidence.isEmpty())
                .canHandover(scope.canManage() && "ARRIVED".equals(status) && destHubId.equals(receiptHubId) && handoverId == null)
                .ocrEvidence(evidence)
                .build();
    }

    // 같은 허브 · 입차 · 허용/보류 · 같은 차량 · 배차(출발했으면 출발) 이후 (최근 5건)
    private List<OcrEvidence> ocrEvidence(Long hubId, String vehicleNo, OffsetDateTime since) {
        if (vehicleNo == null || since == null) return List.of();
        return jdbc.query("SELECT log_id, recognized_plate, decision, ocr_confidence, dock_no, seal_status, created_at "
                        + "FROM hub_vehicle_logs WHERE hub_id = ? AND direction = 'ENTRY' AND decision IN ('ALLOW', 'HOLD') "
                        + "AND replace(recognized_plate, ' ', '') = ? AND created_at >= ? ORDER BY log_id DESC LIMIT 5",
                (rs, i) -> OcrEvidence.builder()
                        .logId(rs.getLong("log_id"))
                        .recognizedPlate(rs.getString("recognized_plate"))
                        .decision(rs.getString("decision"))
                        .ocrConfidence((Double) rs.getObject("ocr_confidence"))
                        .dockNo((Integer) rs.getObject("dock_no"))
                        .sealStatus(rs.getString("seal_status"))
                        .createdAt(rs.getObject("created_at", OffsetDateTime.class))
                        .build(),
                hubId, vehicleNo, since);
    }

    private String photoJson(List<String> photoUrls) {
        if (photoUrls == null || photoUrls.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(photoUrls);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("사진 주소 형식이 올바르지 않습니다.");
        }
    }

    static String normalizePlate(String plate) {
        return plate == null ? null : plate.replace(" ", "").trim();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }
}
