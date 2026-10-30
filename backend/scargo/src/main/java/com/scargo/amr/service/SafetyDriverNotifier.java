package com.scargo.amr.service;

import com.scargo.Enum.NotificationType;
import com.scargo.dto.NotificationCreateRequest;
import com.scargo.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 26.10.29 허브 화재 경보 → 기사 앱 알림
 *  경보가 새로 발령되면 허브로 오는 중이거나 허브에서 일할 기사에게 알림(알림 내역 + 폰 알림)을 보내고,
 *  화재 종료 때 같은 기사들에게 종료 알림을 한 번 더 보낸다.
 *  대상: 승인된 컨테이너 기사 · 지선 기사 전원 (26.10.29 QA: 운행 여부와 상관없이 알림 목록에 남도록)
 */
@Component
public class SafetyDriverNotifier {
    private static final Logger log = LoggerFactory.getLogger(SafetyDriverNotifier.class);
    private final JdbcTemplate jdbc;
    private final NotificationService notificationService;
    private final Set<Long> notified = new LinkedHashSet<>(); // 이번 경보에 알림을 받은 기사 (종료 알림 대상)

    public SafetyDriverNotifier(JdbcTemplate jdbc, NotificationService notificationService) {
        this.jdbc = jdbc;
        this.notificationService = notificationService;
    }

    // 26.10.29 QA: 화재는 안전 알림이라 승인된 기사 전원에게 보낸다 (예전: 진행 중 운행이 있는 기사만 → 운행 없는 기사는 알림 목록에 안 남음)
    private List<Long> targets() {
        return jdbc.queryForList("""
                SELECT account_id FROM accounts
                 WHERE user_type IN ('CONTAINER_DRIVER','BRANCH_DRIVER') AND approval_status = 'APPROVED'""", Long.class);
    }

    public synchronized void started(String kind, String label) {
        String title, msg;
        if ("batt".equals(kind)) {
            title = "[긴급] 허브 AMR 배터리 화재";
            msg = (label == null || label.isBlank() ? "" : label + " · ") + "허브는 정상 운영 중입니다. 화재 구역에 접근하지 말고 현장 안내를 따르세요.";
        } else {
            title = "[긴급] 허브 화재 발생";
            msg = "허브에 화재가 발생했습니다. 허브로 진입하지 말고, 허브 안에 있다면 즉시 대피하세요. 관제 안내가 있을 때까지 대기하세요.";
        }
        notified.clear();
        try {
            for (Long id : targets()) { send(id, title, msg); notified.add(id); }
        } catch (Exception e) {
            log.warn("화재 경보 기사 알림 실패: {}", e.getMessage());
        }
    }

    public synchronized void ended(String kind, String label) {
        String title = "batt".equals(kind) ? "허브 AMR 배터리 화재 종료" : "허브 화재 종료";
        String msg = "화재 상황이 종료되었습니다. 관제 안내에 따라 정상 운행하세요.";
        try {
            Set<Long> to = new LinkedHashSet<>(notified);
            to.addAll(targets());
            for (Long id : to) send(id, title, msg);
        } catch (Exception e) {
            log.warn("화재 종료 기사 알림 실패: {}", e.getMessage());
        }
        notified.clear();
    }

    private void send(Long accountId, String title, String message) {
        try {
            notificationService.createNotificationInNewTx(NotificationCreateRequest.builder()
                    .accountId(accountId).title(title).message(message).notificationType(NotificationType.SYSTEM).build());
        } catch (Exception e) {
            log.warn("알림 저장 실패 ({}): {}", accountId, e.getMessage());
        }
    }
}
