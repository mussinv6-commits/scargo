package com.scargo.service;

import com.scargo.security.HubScopePolicy;
import com.scargo.security.HubScopePolicy.HubScope;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 26.10.24 운영 현황판 (읽기 전용 집계). 처리 버튼은 화면이 기존 API(하역 완료 · 점검표 · 예외 접수)를 부른다.
 *  ADMIN · CONTROL 전체 허브 / HUB_OP 소속 허브
 *  항목: 숫자 요약 · 하역 대기 목록(대기 분) · 도크 점유 · 점검 불합격(최근 7일) · 지연 오더(ETA 초과) · 미처리 예외 · 사고
 */
@Service
@RequiredArgsConstructor
public class OpsOverviewService {

    private final JdbcTemplate jdbc;
    private final HubScopePolicy hubScopePolicy;

    @Transactional(readOnly = true)
    public Map<String, Object> overview(Authentication authentication) {
        HubScope scope = hubScopePolicy.view(authentication);
        Long hub = scope.hubId();

        List<Map<String, Object>> unloads = rows(
                "SELECT r.receipt_id AS \"receiptId\", r.hub_id AS \"hubId\", r.container_no AS \"containerNo\", r.vehicle_no AS \"vehicleNo\", "
                        + "r.status, d.dock_code AS \"dockCode\", r.arrived_at AS \"arrivedAt\", "
                        + "FLOOR(EXTRACT(EPOCH FROM (now() - r.arrived_at)) / 60)::bigint AS \"waitingMin\" "
                        + "FROM hub_inbound_receipts r LEFT JOIN hub_docks d ON d.dock_id = r.dock_id "
                        + "WHERE r.status IN ('ARRIVED', 'UNLOADING')" + (hub == null ? "" : " AND r.hub_id = ?")
                        + " ORDER BY r.arrived_at LIMIT 100", hub);

        List<Map<String, Object>> docks = rows(
                "SELECT d.dock_id AS \"dockId\", d.hub_id AS \"hubId\", d.dock_code AS \"dockCode\", d.dock_type AS \"dockType\", "
                        + "EXISTS (SELECT 1 FROM hub_inbound_receipts r WHERE r.dock_id = d.dock_id AND r.status IN ('ARRIVED', 'UNLOADING')) AS occupied "
                        + "FROM hub_docks d" + (hub == null ? "" : " WHERE d.hub_id = ?") + " ORDER BY d.hub_id, d.dock_type DESC, d.dock_code", hub);

        List<Map<String, Object>> failed = rows(
                "SELECT inspection_id AS \"inspectionId\", container_no AS \"containerNo\", hub_id AS \"hubId\", finalized_at AS \"finalizedAt\", "
                        // 26.10.29 QA: 현황판 '점검 불합격'에 사유 표시 (불합격 항목 + 메모)
                        + "items::text AS \"items\", memo AS \"memo\" "
                        + "FROM container_inspections WHERE record_status = 'FINALIZED' AND overall_status = 'FAIL' "
                        + "AND finalized_at >= now() - interval '7 days'" + (hub == null ? "" : " AND hub_id = ?")
                        + " ORDER BY finalized_at DESC LIMIT 50", hub);

        List<Map<String, Object>> delayed = rows(
                "SELECT order_id AS \"orderId\", order_no AS \"orderNo\", vehicle_no AS \"vehicleNo\", container_no AS \"containerNo\", "
                        + "dest_hub_id AS \"destHubId\", eta, FLOOR(EXTRACT(EPOCH FROM (now() - eta)) / 60)::bigint AS \"lateMin\" "
                        + "FROM transport_orders WHERE status IN ('GATE_OUT', 'IN_TRANSIT') AND eta IS NOT NULL AND eta < now()"
                        + (hub == null ? "" : " AND dest_hub_id = ?") + " ORDER BY eta LIMIT 50", hub);

        List<Map<String, Object>> incidents = rows(
                "SELECT incident_id AS \"incidentId\", incident_type AS \"type\", severity, title, status, assignee, "
                        + "hub_id AS \"hubId\", created_at AS \"createdAt\" FROM ops_incidents WHERE status <> 'RESOLVED'"
                        + (hub == null ? "" : " AND hub_id = ?")
                        + " ORDER BY CASE severity WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END, created_at LIMIT 50", hub);

        long occupied = docks.stream().filter(d -> Boolean.TRUE.equals(d.get("occupied"))).count();
        long highIncidents = incidents.stream().filter(i -> "HIGH".equals(i.get("severity"))).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("waitingUnload", unloads.size());
        summary.put("docksTotal", docks.size());
        summary.put("docksOccupied", occupied);
        summary.put("failedInspections7d", failed.size());
        summary.put("delayedOrders", delayed.size());
        summary.put("openIncidents", incidents.size());
        summary.put("highIncidents", highIncidents);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", hub == null ? "전체 허브" : "소속 허브 " + hub);
        result.put("canManage", scope.canManage());
        result.put("summary", summary);
        result.put("unloads", unloads);
        result.put("docks", docks);
        result.put("failedInspections", failed);
        result.put("delayedOrders", delayed);
        result.put("incidents", incidents);
        return result;
    }

    private List<Map<String, Object>> rows(String sql, Long hub) {
        List<Object> args = new ArrayList<>();
        if (hub != null) {
            args.add(hub);
        }
        return jdbc.queryForList(sql, args.toArray());
    }
}
