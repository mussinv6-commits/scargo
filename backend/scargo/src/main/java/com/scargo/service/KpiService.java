package com.scargo.service;

import com.scargo.security.HubScopePolicy;
import com.scargo.security.HubScopePolicy.HubScope;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 26.10.24 KPI 일별 추이 (한국 날짜 기준, 오늘 포함 최근 N일, 읽기 전용)
 *  입고 건수 · 평균 하역 시간(도착→하역 완료) · 평균 운송 시간(출발→도착) · 평균 인계 대기(도착→인계)
 *  · 점검 확정 건수 · 불합격 건수.  ADMIN · CONTROL 전체 허브 / HUB_OP 소속 허브
 */
@Service
@RequiredArgsConstructor
public class KpiService {

    private static final String KST = "'Asia/Seoul'";

    private final JdbcTemplate jdbc;
    private final HubScopePolicy hubScopePolicy;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> trend(Authentication authentication, Integer days) {
        HubScope scope = hubScopePolicy.view(authentication);
        int n = days == null ? 14 : Math.max(1, Math.min(60, days));
        Long hub = scope.hubId();
        String rh = hub == null ? "" : " AND r.hub_id = ?";
        String oh = hub == null ? "" : " AND o.dest_hub_id = ?";
        String ih = hub == null ? "" : " AND i.hub_id = ?";

        String sql = "SELECT to_char(d.day, 'YYYY-MM-DD') AS \"day\", "
                + "(SELECT COUNT(*) FROM hub_inbound_receipts r WHERE (r.arrived_at AT TIME ZONE " + KST + ")::date = d.day" + rh + ") AS \"arrivals\", "
                + "(SELECT ROUND(AVG(EXTRACT(EPOCH FROM (r.unloaded_at - r.arrived_at)) / 60)::numeric, 1) FROM hub_inbound_receipts r "
                + "  WHERE r.unloaded_at IS NOT NULL AND (r.unloaded_at AT TIME ZONE " + KST + ")::date = d.day" + rh + ") AS \"avgUnloadMin\", "
                + "(SELECT ROUND(AVG(EXTRACT(EPOCH FROM (o.arrived_at - o.departed_at)) / 60)::numeric, 1) FROM transport_orders o "
                + "  WHERE o.departed_at IS NOT NULL AND o.arrived_at IS NOT NULL AND (o.arrived_at AT TIME ZONE " + KST + ")::date = d.day" + oh + ") AS \"avgTransitMin\", "
                + "(SELECT ROUND(AVG(EXTRACT(EPOCH FROM (o.handed_over_at - o.arrived_at)) / 60)::numeric, 1) FROM transport_orders o "
                + "  WHERE o.arrived_at IS NOT NULL AND o.handed_over_at IS NOT NULL AND (o.handed_over_at AT TIME ZONE " + KST + ")::date = d.day" + oh + ") AS \"avgHandoverMin\", "
                + "(SELECT COUNT(*) FROM container_inspections i WHERE i.record_status = 'FINALIZED' "
                + "  AND (i.finalized_at AT TIME ZONE " + KST + ")::date = d.day" + ih + ") AS \"inspections\", "
                + "(SELECT COUNT(*) FROM container_inspections i WHERE i.record_status = 'FINALIZED' AND i.overall_status = 'FAIL' "
                + "  AND (i.finalized_at AT TIME ZONE " + KST + ")::date = d.day" + ih + ") AS \"failed\" "
                + "FROM generate_series(((now() AT TIME ZONE " + KST + ")::date - ?)::timestamp, "
                + "(now() AT TIME ZONE " + KST + ")::date::timestamp, interval '1 day') AS d(day) ORDER BY d.day";

        List<Object> args = new ArrayList<>();
        if (hub != null) {
            for (int i = 0; i < 6; i++) {
                args.add(hub);
            }
        }
        args.add(n - 1);
        return jdbc.queryForList(sql, args.toArray());
    }
}
