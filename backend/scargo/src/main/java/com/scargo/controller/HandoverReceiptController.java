package com.scargo.controller;

import com.scargo.app.security.CurrentAccount;
import com.scargo.entity.Account;
import com.scargo.security.HubScopePolicy;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;

/**
 * 26.10.29 인수 내역 · 인수증 (허브 인계 기록 handovers 기준, 읽기 전용)
 *  - 앱에서 기사가 받은 인수자 이름 · 서명 · 사진, 웹 도착 · 점검에서 확정한 인계 모두 같은 표(handovers)에 있다
 *  GET /api/handovers?from=&to=&q=       웹 인수 내역 (ADMIN · CONTROL 전체, HUB_OP 소속 허브, 운송사 담당자 자기 회사)
 *  GET /api/handovers/{orderId}           인수증 1건 (같은 권한 범위)
 *  GET /api/app/handovers                 앱: 내가 인계한 최근 인수 내역 (컨테이너 운송기사)
 *  GET /api/app/handovers/{orderId}       앱: 인수증 1건 (내 오더만)
 */
@RestController
public class HandoverReceiptController {

    private final JdbcTemplate jdbc;
    private final HubScopePolicy hubScope;

    public HandoverReceiptController(JdbcTemplate jdbc, HubScopePolicy hubScope) {
        this.jdbc = jdbc;
        this.hubScope = hubScope;
    }

    private static final String SELECT = """
            SELECT h.handover_id AS "handoverId", o.order_id AS "orderId", o.order_no AS "orderNo", o.container_no AS "containerNo",
                   o.vehicle_no AS "vehicleNo", t.trailer_no AS "trailerNo", o.shipper_name AS "shipperName",
                   o.company_id AS "companyId", co.company_name AS "companyName", co.business_no AS "companyBizNo",
                   d.user_name AS "driverName", d.phone_num AS "driverPhone",
                   tm.terminal_name AS "originName", tm.port_name AS "originPort", o.dest_hub_id AS "hubId", hb.hub_name AS "hubName",
                   o.assigned_at AS "assignedAt", o.departed_at AS "departedAt", o.arrived_at AS "arrivedAt",
                   COALESCE(h.completed_at, o.handed_over_at) AS "completedAt",
                   h.receiver_name AS "receiverName", h.signature_url AS "signatureUrl", h.photo_urls::text AS "photoUrls",
                   h.seal_no AS "sealNo", h.seal_ok AS "sealOk", h.damage_free AS "damageFree", h.container_no_confirmed AS "containerNoConfirmed",
                   o.gross_weight_kg AS "grossWeightKg", o.planned_distance_km AS "distanceKm", o.damage_noted AS "damageNoted"
              FROM handovers h
              JOIN transport_orders o ON o.order_id = h.order_id
              LEFT JOIN accounts d ON d.account_id = o.driver_account_id
              LEFT JOIN companies co ON co.company_id = o.company_id
              LEFT JOIN terminals tm ON tm.terminal_id = o.origin_terminal_id
              LEFT JOIN hubs hb ON hb.hub_id = o.dest_hub_id
              LEFT JOIN trucks t ON t.vehicle_no = o.vehicle_no
            """;

    /** 웹 계정의 볼 수 있는 범위 → WHERE 조건 (params 에 값 추가) */
    private String scope(Authentication auth, List<Object> params) {
        Account a = hubScope.currentAccount(auth);
        Account.UserType t = a.getUserType();
        if (t == Account.UserType.ADMIN || t == Account.UserType.CONTROL) return "TRUE";
        if (t == Account.UserType.HUB_OP) {
            HubScopePolicy.HubScope s = hubScope.view(auth);
            if (s.allHubs()) return "TRUE";
            params.add(s.hubId());
            return "o.dest_hub_id = ?";
        }
        if (t == Account.UserType.CORPORATE_APPROVED && a.getCompanyId() != null) {
            params.add(a.getCompanyId());
            return "o.company_id = ?";
        }
        throw new AccessDeniedException("인수 내역을 볼 수 있는 계정이 아닙니다.");
    }

    @GetMapping("/api/handovers")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','HUB_OP','CARRIER')")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Authentication auth,
                                          @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(name = "q", required = false) String q) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE ").append(scope(auth, params));
        if (from != null && to != null && to.isBefore(from)) throw new IllegalArgumentException("종료일이 시작일보다 빠릅니다.");
        if (from != null) { where.append(" AND COALESCE(h.completed_at, o.handed_over_at) >= ?"); params.add(Timestamp.valueOf(from.atStartOfDay())); }
        if (to != null) { where.append(" AND COALESCE(h.completed_at, o.handed_over_at) < ?"); params.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay())); }
        if (q != null && !q.isBlank()) {
            where.append(" AND (o.order_no ILIKE ? OR o.container_no ILIKE ? OR o.vehicle_no ILIKE ? OR h.receiver_name ILIKE ? OR d.user_name ILIKE ? OR h.seal_no ILIKE ?)");
            String like = "%" + q.trim() + "%";
            for (int i = 0; i < 6; i++) params.add(like);
        }
        return jdbc.queryForList(SELECT + where + " ORDER BY COALESCE(h.completed_at, o.handed_over_at) DESC NULLS LAST, h.handover_id DESC LIMIT 500", params.toArray());
    }

    @GetMapping("/api/handovers/{orderId}")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','HUB_OP','CARRIER')")
    @Transactional(readOnly = true)
    public Map<String, Object> one(Authentication auth, @PathVariable("orderId") Long orderId) {
        List<Object> params = new ArrayList<>();
        String sc = scope(auth, params);
        params.add(orderId);
        List<Map<String, Object>> l = jdbc.queryForList(SELECT + " WHERE " + sc + " AND o.order_id = ?", params.toArray());
        if (l.isEmpty()) throw new IllegalArgumentException("인수 기록이 없거나 볼 수 없는 오더입니다.");
        return l.get(0);
    }

    @GetMapping("/api/app/handovers")
    @PreAuthorize("hasRole('CONTAINER_DRIVER')")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine() {
        return jdbc.queryForList(SELECT + " WHERE o.driver_account_id = ? ORDER BY COALESCE(h.completed_at, o.handed_over_at) DESC NULLS LAST LIMIT 50", CurrentAccount.id());
    }

    @GetMapping("/api/app/handovers/{orderId}")
    @PreAuthorize("hasRole('CONTAINER_DRIVER')")
    @Transactional(readOnly = true)
    public Map<String, Object> mineOne(@PathVariable("orderId") Long orderId) {
        List<Map<String, Object>> l = jdbc.queryForList(SELECT + " WHERE o.driver_account_id = ? AND o.order_id = ?", CurrentAccount.id(), orderId);
        if (l.isEmpty()) throw new IllegalArgumentException("인수 기록이 없습니다.");
        return l.get(0);
    }
}
