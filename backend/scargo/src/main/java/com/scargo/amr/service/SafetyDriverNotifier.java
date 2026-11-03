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
 * 26.10.29 허브 화재 경보 → 알림(기사 앱 + 웹 알림 내역)
 *  경보가 새로 발령되면 대상 계정에 알림을 보내고, 화재 종료 때 같은 대상에게 종료 알림을 한 번 더 보낸다.
 *  대상: 승인된 컨테이너·지선 기사 + 웹 사용자(ADMIN·CONTROL·HUB_OP·CENTER_OP)
 *  26.11.01: 예전에는 기사만 넣어서 웹 「알림 내역」에 화재가 안 보였음 → 관제·관리자 계정에도 SYSTEM 알림 저장
 */
@Component
public class SafetyDriverNotifier {
    private static final Logger log = LoggerFactory.getLogger(SafetyDriverNotifier.class);
    private final JdbcTemplate jdbc;
    private final NotificationService notificationService;
    private final Set<Long> notified = new LinkedHashSet<>(); // 이번 경보에 알림을 받은 계정 (종료 알림 대상)

    public SafetyDriverNotifier(JdbcTemplate jdbc, NotificationService notificationService) {
        this.jdbc = jdbc;
        this.notificationService = notificationService;
    }

    // 26.11.01: 기사(앱 알림 목록) + 웹 관제/관리자(알림 내역·종 버튼)
    private List<Long> targets() {
        return jdbc.queryForList("""
                SELECT account_id FROM accounts
                 WHERE approval_status = 'APPROVED'
                   AND user_type IN (
                     'CONTAINER_DRIVER','BRANCH_DRIVER',
                     'ADMIN','CONTROL','HUB_OP','CENTER_OP'
                   )""", Long.class);
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
