package com.scargo.app.controller;

import com.scargo.app.security.CurrentAccount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 26.10.29 앱 알림 1건 읽음 처리 (알림 내역에서 누르면 상세를 열면서 읽음)
 *  POST /api/app/notifications/{id}/read  — 내 알림만 (다른 사람 알림 번호면 아무것도 바뀌지 않음)
 */
@RestController
@RequestMapping("/api/app")
public class AppNotificationReadController {
    private final JdbcTemplate jdbc;

    public AppNotificationReadController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/notifications/{id}/read")
    @Transactional
    public Map<String, Object> read(@PathVariable("id") Long id) {
        int n = jdbc.update("UPDATE notifications SET is_read = TRUE, read_at = COALESCE(read_at, now()) WHERE notification_id = ? AND account_id = ?", id, CurrentAccount.id());
        return Map.of("id", id, "updated", n);
    }
}
