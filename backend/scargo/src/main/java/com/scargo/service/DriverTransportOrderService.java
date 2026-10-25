package com.scargo.service;

import com.scargo.entity.Account;
import com.scargo.security.HubScopePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 26.10.23 컨테이너 기사 앱의 운송 오더 (항만 → 허브)
 *
 * 조회: 본인에게 배정된 오더(transport_orders.driver_account_id = 본인)와 차량 · 허브 · 씰 · 입고 · 인계 상태
 * 출발: 기사가 직접 누르는 "운송 출발" → 오더 IN_TRANSIT + departed_at, 운송 구간 IN_TRANSIT, trip_events DEPART
 *   - 본인 배정 오더 + 본인 배정 차량 + 같은 업체일 때만
 *   - ASSIGNED · LOADED · GATE_OUT 에서만 (이미 출발 · 도착이면 409)
 *   - LOADED(상차) · GATE_OUT(항만 게이트 통과)은 근거 기록(gate_logs 등)과 연결하는 기능이 없어 만들지 않는다
 */
@Service
@RequiredArgsConstructor
public class DriverTransportOrderService {

    private static final Set<String> DEPARTABLE = Set.of("ASSIGNED", "LOADED", "GATE_OUT");

    private final JdbcTemplate jdbc;
    private final HubScopePolicy hubScopePolicy;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> myOrders(Authentication authentication) {
        Account driver = requireDriver(authentication);
        return jdbc.queryForList("SELECT o.order_id AS \"orderId\", o.order_no AS \"orderNo\", o.status, o.company_id AS \"companyId\", "
                + "c.company_name AS \"companyName\", o.container_no AS \"containerNo\", o.vehicle_no AS \"vehicleNo\", "
                + "(t.assigned_account_id = o.driver_account_id) AS \"myVehicle\", "
                + "o.dest_hub_id AS \"destHubId\", h.hub_name AS \"destHubName\", "
                + "o.assigned_at AS \"assignedAt\", o.departed_at AS \"departedAt\", o.arrived_at AS \"arrivedAt\", "
                + "o.handed_over_at AS \"handedOverAt\", l.status AS \"legStatus\", "
                + "EXISTS (SELECT 1 FROM container_seals s WHERE s.shipment_id = o.shipment_id AND s.container_no = o.container_no "
                + "        AND s.status = 'ACTIVE') AS \"sealRegistered\", "
                + "(SELECT ci.overall_status || '/' || ci.record_status FROM container_inspections ci WHERE ci.order_id = o.order_id "
                + "  AND ci.record_status <> 'CANCELLED' ORDER BY ci.created_at DESC LIMIT 1) AS \"latestInspection\" "
                + "FROM transport_orders o "
                + "JOIN companies c ON c.company_id = o.company_id "
                + "JOIN hubs h ON h.hub_id = o.dest_hub_id "
                + "LEFT JOIN trucks t ON t.vehicle_no = o.vehicle_no "
                + "LEFT JOIN shipment_legs l ON l.assignment_id = o.assignment_id "
                + "WHERE o.driver_account_id = ? AND o.status <> 'CANCELED' "
                + "ORDER BY (o.status = 'HANDED_OVER'), o.assigned_at DESC NULLS LAST, o.order_id DESC",
                driver.getAccountId());
    }

    @Transactional
    public Map<String, Object> depart(Authentication authentication, Long orderId) {
        Account driver = requireDriver(authentication);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT o.order_no, o.status, o.company_id, o.vehicle_no, o.driver_account_id, "
                + "o.assignment_id FROM transport_orders o WHERE o.order_id = ? FOR UPDATE", orderId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "오더가 없습니다: " + orderId);
        }
        Map<String, Object> o = rows.get(0);
        Long vehicleOwner = o.get("vehicle_no") == null ? null : jdbc.queryForList(
                "SELECT assigned_account_id FROM trucks WHERE vehicle_no = ?", Long.class, o.get("vehicle_no"))
                .stream().findFirst().orElse(null);
        if (!driver.getAccountId().equals(toLong(o.get("driver_account_id")))
                || !driver.getAccountId().equals(vehicleOwner)
                || !toLong(o.get("company_id")).equals(driver.getCompanyId())) {
            throw new AccessDeniedException("본인에게 배정되고 본인 차량으로 운송하는 오더만 출발 처리할 수 있습니다.");
        }
        String status = String.valueOf(o.get("status"));
        if (!DEPARTABLE.contains(status)) {
            throw new IllegalStateException("출발 처리할 수 없는 오더 상태입니다 (현재 " + status + ")");
        }
        List<String> legStatus = jdbc.queryForList("SELECT status FROM shipment_legs WHERE assignment_id = ? FOR UPDATE",
                String.class, o.get("assignment_id"));
        if (legStatus.isEmpty() || !"ASSIGNED".equals(legStatus.get(0))) {
            throw new IllegalStateException("운송 구간이 배차 상태가 아닙니다 (현재 " + (legStatus.isEmpty() ? "없음" : legStatus.get(0)) + ")");
        }

        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("UPDATE transport_orders SET status = 'IN_TRANSIT', departed_at = ? WHERE order_id = ?", now, orderId);
        jdbc.update("UPDATE shipment_legs SET status = 'IN_TRANSIT', departed_at = ? WHERE assignment_id = ?", now, o.get("assignment_id"));
        jdbc.update("INSERT INTO trip_events (order_id, event_type, message, occurred_at) VALUES (?, 'DEPART', ?, ?)",
                orderId, "기사 운송 출발 · " + driver.getUserId() + " · " + o.get("vehicle_no"), now);
        return Map.of("orderId", orderId, "orderNo", o.get("order_no"), "status", "IN_TRANSIT", "departedAt", now);
    }

    private Account requireDriver(Authentication authentication) {
        Account account = hubScopePolicy.currentAccount(authentication);
        if (account.getUserType() != Account.UserType.CONTAINER_DRIVER) {
            throw new AccessDeniedException("컨테이너 운송기사만 사용할 수 있습니다.");
        }
        return account;
    }

    private static Long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }
}
