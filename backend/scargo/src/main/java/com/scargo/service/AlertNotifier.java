package com.scargo.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 26.10.24 경보 → 알림 벨 연동.  AlertService 의 경보를 1분마다 확인해, 기존 알림(notifications)으로 넣는다.
 *  받는 사람: ADMIN · CONTROL 전체 / HUB_OP 는 소속 허브 경보만 / 지연 오더는 배정 기사에게도
 *  같은 경보를 12시간 안에 다시 보내지 않는다 (제목에 [긴급] 이 붙으면 다른 알림으로 보고 한 번 더 보낸다)
 *  알림 유형: 도착 지연 = DELAY_30MIN, 나머지 = EXCEPTION (기존 chk_notification_type 허용 값)
 *  끄려면 application.yml 에 scargo.alerts.notify-enabled: false
 */
@Component
@EnableScheduling
@RequiredArgsConstructor
public class AlertNotifier {

    private static final Logger log = LoggerFactory.getLogger(AlertNotifier.class);
    private static final Map<String, String> RULE_LABEL = Map.of(
            "UNLOAD_WAIT", "하역 대기", "ORDER_LATE", "도착 지연", "INCIDENT_HIGH", "사고 미처리",
            "REINSPECT", "재점검 필요", "GATE_NOSHOW", "예약 입차 미확인", "BACKUP", "백업 확인");

    private final JdbcTemplate jdbc;
    private final AlertService alertService;
    @org.springframework.beans.factory.annotation.Value("${scargo.alerts.notify-enabled:true}")
    private boolean enabled;

    @Scheduled(initialDelay = 30000, fixedDelay = 60000)
    public void run() {
        if (!enabled) {
            return;
        }
        try {
            List<Map<String, Object>> items = alertService.compute(null);
            List<Long> staff = jdbc.queryForList("SELECT account_id FROM accounts WHERE user_type IN ('ADMIN', 'CONTROL')", Long.class);
            for (Map<String, Object> it : items) {
                Set<Long> to = new LinkedHashSet<>(staff);
                Long hub = (Long) it.get("hubId");
                if (hub != null) {
                    to.addAll(jdbc.queryForList("SELECT account_id FROM accounts WHERE user_type = 'HUB_OP' AND hub_id = ?", Long.class, hub));
                }
                if (it.get("driverAccountId") != null) {
                    to.add((Long) it.get("driverAccountId"));
                }
                String rule = (String) it.get("rule");
                String title = ("CRITICAL".equals(it.get("level")) ? "[긴급] " : "[경보] ") + RULE_LABEL.getOrDefault(rule, rule);
                String type = "ORDER_LATE".equals(rule) ? "DELAY_30MIN" : "EXCEPTION";
                Long ref = (Long) it.get("refId");
                String message = it.get("title") + " — " + it.get("detail");
                for (Long account : to) {
                    Integer dup = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM notifications WHERE account_id = ? AND notification_type = ? AND title = ? "
                                    + "AND reference_id IS NOT DISTINCT FROM ? AND created_at > now() - interval '12 hours'",
                            Integer.class, account, type, title, ref);
                    if (dup != null && dup == 0) {
                        jdbc.update("INSERT INTO notifications (account_id, title, message, notification_type, reference_id) VALUES (?, ?, ?, ?, ?)",
                                account, title, message, type, ref);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("경보 알림 발송 실패: {}", e.getMessage());
        }
    }
}
