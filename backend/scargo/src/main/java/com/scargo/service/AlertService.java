package com.scargo.service;

import com.scargo.security.HubScopePolicy;
import com.scargo.security.HubScopePolicy.HubScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 26.10.24 경보 규칙 (읽기 전용, 조회할 때마다 계산).  level: CRITICAL(긴급) / WARN(주의)
 *  UNLOAD_WAIT   하역 대기 60분 초과(WARN) · 120분 초과(CRITICAL)
 *  ORDER_LATE    ETA 30분 이상 지났는데 아직 도착 안 한 운송 오더
 *  INCIDENT_HIGH 심각도 높음 사고가 2시간 넘게 미처리(CRITICAL) / 미해결 사고가 24시간 넘음(WARN)
 *  REINSPECT     점검 불합격 후 24시간 안에 재점검이 없음
 *  GATE_NOSHOW   게이트 예약 시각이 30분 지났는데 입차 확인이 없음 (ADMIN · CONTROL 만)
 *  BACKUP        36시간 넘게 백업 파일이 없음 (ADMIN · CONTROL 만)
 * 기준 시간은 application.yml 의 scargo.alerts.* 로 바꿀 수 있다.
 */
@Service
public class AlertService {

    private final JdbcTemplate jdbc;
    private final HubScopePolicy hubScopePolicy;
    private final int unloadWarnMin;
    private final int unloadCriticalMin;
    private final int lateGraceMin;
    private final int incidentHighMin;
    private final String backupDir;

    public AlertService(JdbcTemplate jdbc, HubScopePolicy hubScopePolicy,
                        @Value("${scargo.alerts.unload-warn-min:60}") int unloadWarnMin,
                        @Value("${scargo.alerts.unload-critical-min:120}") int unloadCriticalMin,
                        @Value("${scargo.alerts.late-grace-min:30}") int lateGraceMin,
                        @Value("${scargo.alerts.incident-high-min:120}") int incidentHighMin,
                        @Value("${scargo.backup.dir:../../백업}") String backupDir) {
        this.jdbc = jdbc;
        this.hubScopePolicy = hubScopePolicy;
        this.unloadWarnMin = unloadWarnMin;
        this.unloadCriticalMin = unloadCriticalMin;
        this.lateGraceMin = lateGraceMin;
        this.incidentHighMin = incidentHighMin;
        this.backupDir = backupDir;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> alerts(Authentication authentication) {
        HubScope scope = hubScopePolicy.view(authentication);
        List<Map<String, Object>> list = compute(scope.hubId());
        long critical = list.stream().filter(m -> "CRITICAL".equals(m.get("level"))).count();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("critical", critical);
        body.put("warn", list.size() - critical);
        body.put("items", list);
        return body;
    }

    /** hub = null 이면 전체 허브(예약 · 백업 규칙 포함). 알림 발송기(AlertNotifier)도 이 목록을 쓴다 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> compute(Long hub) {
        List<Map<String, Object>> list = new ArrayList<>();

        // 하역 대기
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT receipt_id, hub_id, container_no, vehicle_no, FLOOR(EXTRACT(EPOCH FROM (now() - arrived_at)) / 60)::bigint AS wait_min "
                        + "FROM hub_inbound_receipts WHERE status IN ('ARRIVED', 'UNLOADING') AND arrived_at < now() - (? * interval '1 minute')"
                        + (hub == null ? "" : " AND hub_id = ?") + " ORDER BY arrived_at",
                hub == null ? new Object[]{unloadWarnMin} : new Object[]{unloadWarnMin, hub})) {
            long wait = ((Number) r.get("wait_min")).longValue();
            list.add(item(wait >= unloadCriticalMin ? "CRITICAL" : "WARN", "UNLOAD_WAIT", "board",
                    "하역 대기 " + wait + "분 · " + nz(r.get("container_no")) + " (" + r.get("vehicle_no") + ")",
                    "하역 완료 처리가 필요합니다.", (Long) r.get("hub_id"), (Long) r.get("receipt_id"), null));
        }
        // 도착 지연
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT order_id, dest_hub_id, driver_account_id, order_no, container_no, FLOOR(EXTRACT(EPOCH FROM (now() - eta)) / 60)::bigint AS late_min FROM transport_orders "
                        + "WHERE status IN ('GATE_OUT', 'IN_TRANSIT') AND eta IS NOT NULL AND eta < now() - (? * interval '1 minute')"
                        + (hub == null ? "" : " AND dest_hub_id = ?") + " ORDER BY eta",
                hub == null ? new Object[]{lateGraceMin} : new Object[]{lateGraceMin, hub})) {
            list.add(item("WARN", "ORDER_LATE", "board",
                    "도착 지연 " + r.get("late_min") + "분 · " + r.get("order_no") + " " + nz(r.get("container_no")),
                    "예정 시각이 지났는데 아직 허브에 도착하지 않았습니다.", (Long) r.get("dest_hub_id"), (Long) r.get("order_id"), (Long) r.get("driver_account_id")));
        }
        // 사고
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT incident_id, hub_id, title, severity, FLOOR(EXTRACT(EPOCH FROM (now() - created_at)) / 60)::bigint AS age_min FROM ops_incidents "
                        + "WHERE status <> 'RESOLVED' AND ((severity = 'HIGH' AND created_at < now() - (? * interval '1 minute')) OR created_at < now() - interval '24 hours')"
                        + (hub == null ? "" : " AND hub_id = ?") + " ORDER BY created_at",
                hub == null ? new Object[]{incidentHighMin} : new Object[]{incidentHighMin, hub})) {
            boolean high = "HIGH".equals(r.get("severity"));
            long age = ((Number) r.get("age_min")).longValue();
            list.add(item(high && age >= incidentHighMin ? "CRITICAL" : "WARN", "INCIDENT_HIGH", "incidents",
                    "사고 #" + r.get("incident_id") + " 미처리 " + (age / 60) + "시간 · " + r.get("title"),
                    high ? "심각도 높음 사고가 처리되지 않았습니다." : "24시간 넘게 해결되지 않은 사고입니다.",
                    (Long) r.get("hub_id"), (Long) r.get("incident_id"), null));
        }
        // 불합격 후 재점검 없음
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT i.inspection_id, i.hub_id, i.driver_account_id, i.container_no FROM container_inspections i WHERE i.record_status = 'FINALIZED' AND i.overall_status = 'FAIL' "
                        + "AND i.finalized_at > now() - interval '24 hours' "
                        + "AND NOT EXISTS (SELECT 1 FROM container_inspections n WHERE n.previous_inspection_id = i.inspection_id)"
                        + (hub == null ? "" : " AND i.hub_id = ?") + " ORDER BY i.finalized_at DESC",
                hub == null ? new Object[]{} : new Object[]{hub})) {
            list.add(item("WARN", "REINSPECT", "board",
                    "재점검 필요 · 점검 #" + r.get("inspection_id") + " " + nz(r.get("container_no")),
                    "불합격 점검 이후 재점검 기록이 없습니다.", (Long) r.get("hub_id"), (Long) r.get("inspection_id"), null));
        }
        if (hub == null) {
            for (Map<String, Object> r : jdbc.queryForList(
                    "SELECT reservation_id, vehicle_no FROM gate_reservations WHERE status = 'BOOKED' AND planned_at < now() - interval '30 minutes' ORDER BY planned_at")) {
                list.add(item("WARN", "GATE_NOSHOW", "reservations",
                        "예약 #" + r.get("reservation_id") + " " + r.get("vehicle_no") + " 입차 미확인",
                        "예약 시각이 30분 지났습니다. 입차 확인 또는 불참 처리가 필요합니다.", null, (Long) r.get("reservation_id"), null));
            }
            long newest = 0;
            File[] files = new File(backupDir).listFiles((d, n) -> n.endsWith(".dump"));
            if (files != null) {
                for (File f : files) {
                    newest = Math.max(newest, f.lastModified());
                }
            }
            if (newest == 0 || System.currentTimeMillis() - newest > 36L * 3600 * 1000) {
                list.add(item("WARN", "BACKUP", "board", "자동 백업이 36시간 넘게 없습니다",
                        "scargo_backup.bat 실행 상태를 확인하세요.", null, null, null));
            }
        }
        list.sort((a, b) -> Integer.compare("CRITICAL".equals(a.get("level")) ? 0 : 1, "CRITICAL".equals(b.get("level")) ? 0 : 1));
        return list;
    }

    private static Map<String, Object> item(String level, String rule, String tab, String title, String detail,
                                            Long hubId, Long refId, Long driverAccountId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hubId", hubId);
        m.put("refId", refId);
        m.put("driverAccountId", driverAccountId);
        m.put("level", level);
        m.put("rule", rule);
        m.put("tab", tab);
        m.put("title", title);
        m.put("detail", detail);
        return m;
    }

    private static String nz(Object v) {
        return v == null ? "-" : v.toString();
    }
}
