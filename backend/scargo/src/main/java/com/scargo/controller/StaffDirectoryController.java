package com.scargo.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 26.10.29 QA: 이상 상황(예외·사고) 담당자 선택용 직원 목록 (읽기 전용)
 *  GET /api/staff/assignees  → 관리자 · 통합 관제사 · 허브 관제사 · 물류센터 관제사 (승인된 계정)
 *  화면은 role · hubName · centerName · userName 으로 '곤지암허브 관제사' 같은 실제 권한명을 만든다
 */
@RestController
public class StaffDirectoryController {

    private final JdbcTemplate jdbc;

    public StaffDirectoryController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/staff/assignees")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','HUB_OP','CENTER_OP')")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> assignees() {
        return jdbc.queryForList("""
                SELECT a.account_id AS "accountId", a.user_name AS "userName", a.user_type AS "role",
                       h.hub_name AS "hubName", c.center_name AS "centerName"
                  FROM accounts a
                  LEFT JOIN hubs h ON h.hub_id = a.hub_id
                  LEFT JOIN logistics_centers c ON c.center_id = a.center_id
                 WHERE a.user_type IN ('ADMIN','CONTROL','HUB_OP','CENTER_OP') AND a.approval_status = 'APPROVED'
                 ORDER BY CASE a.user_type WHEN 'ADMIN' THEN 0 WHEN 'CONTROL' THEN 1 WHEN 'HUB_OP' THEN 2 ELSE 3 END, a.user_name
                """);
    }
}
