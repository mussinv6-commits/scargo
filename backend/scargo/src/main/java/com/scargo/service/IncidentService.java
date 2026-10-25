package com.scargo.service;

import com.scargo.entity.Account;
import com.scargo.entity.Account.UserType;
import com.scargo.security.HubScopePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 26.10.24 예외 · 사고 접수 (표: ops_incidents, DB/추가_20261024_운영현황.sql)
 *  접수: ADMIN · CONTROL · HUB_OP · CORPORATE_APPROVED · CONTAINER_DRIVER (HUB_OP 는 소속 허브로 자동 기록)
 *  조회: ADMIN · CONTROL 전체 / HUB_OP 소속 허브 / 그 외는 본인이 접수한 건만
 *  처리(상태 · 담당자 · 조치 내용): ADMIN · CONTROL · HUB_OP(소속 허브). 해결(RESOLVED)은 조치 내용 필수
 */
@Service
@RequiredArgsConstructor
public class IncidentService {

    private static final Set<String> TYPES = Set.of("DAMAGE", "SEAL", "DELAY", "VEHICLE", "SAFETY", "OTHER");
    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH");
    private static final Set<String> STATUSES = Set.of("OPEN", "IN_PROGRESS", "RESOLVED");
    private static final String COLS = "incident_id AS \"incidentId\", incident_type AS \"type\", severity, title, detail, "
            + "container_no AS \"containerNo\", order_id AS \"orderId\", hub_id AS \"hubId\", status, assignee, resolution, "
            + "reported_by_user_id AS \"reportedBy\", created_at AS \"createdAt\", updated_at AS \"updatedAt\", resolved_at AS \"resolvedAt\"";

    private final JdbcTemplate jdbc;
    private final HubScopePolicy hubScopePolicy;

    public record CreateRequest(String type, String severity, String title, String detail,
                                String containerNo, Long orderId, Long hubId) { }

    public record UpdateRequest(String status, String assignee, String resolution) { }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Authentication authentication, String status) {
        Account actor = hubScopePolicy.currentAccount(authentication);
        StringBuilder sql = new StringBuilder("SELECT " + COLS + " FROM ops_incidents WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        UserType type = actor.getUserType();
        if (type == UserType.HUB_OP) {
            sql.append(" AND hub_id = ?");
            args.add(actor.getHubId());
        } else if (type != UserType.ADMIN && type != UserType.CONTROL) {
            sql.append(" AND reported_by_account_id = ?");
            args.add(actor.getAccountId());
        }
        if (status != null && !status.isBlank()) {
            if (!STATUSES.contains(status)) {
                throw new IllegalArgumentException("알 수 없는 상태입니다: " + status);
            }
            sql.append(" AND status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY CASE status WHEN 'RESOLVED' THEN 1 ELSE 0 END, "
                + "CASE severity WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END, created_at DESC LIMIT 300");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public Map<String, Object> create(Authentication authentication, CreateRequest req) {
        Account actor = hubScopePolicy.currentAccount(authentication);
        UserType type = actor.getUserType();
        if (type != UserType.ADMIN && type != UserType.CONTROL && type != UserType.HUB_OP
                && type != UserType.CORPORATE_APPROVED && type != UserType.CONTAINER_DRIVER) {
            throw new AccessDeniedException("예외 · 사고를 접수할 권한이 없습니다.");
        }
        if (req == null || req.type() == null || !TYPES.contains(req.type())) {
            throw new IllegalArgumentException("유형을 선택하세요 (DAMAGE · SEAL · DELAY · VEHICLE · SAFETY · OTHER).");
        }
        String severity = req.severity() == null || req.severity().isBlank() ? "MEDIUM" : req.severity();
        if (!SEVERITIES.contains(severity)) {
            throw new IllegalArgumentException("중요도는 LOW · MEDIUM · HIGH 중 하나입니다.");
        }
        String title = req.title() == null ? "" : req.title().trim();
        if (title.isEmpty() || title.length() > 100) {
            throw new IllegalArgumentException("제목은 1~100자로 입력하세요.");
        }
        String containerNo = req.containerNo() == null || req.containerNo().isBlank() ? null : req.containerNo().trim().toUpperCase();
        if (containerNo != null && containerNo.length() > 11) {
            throw new IllegalArgumentException("컨테이너 번호는 11자 이하입니다.");
        }
        Long hubId = type == UserType.HUB_OP ? actor.getHubId() : req.hubId();
        String detail = req.detail() == null || req.detail().isBlank() ? null : req.detail().trim();
        Long id = jdbc.queryForObject(
                "INSERT INTO ops_incidents (incident_type, severity, title, detail, container_no, order_id, hub_id, "
                        + "reported_by_account_id, reported_by_user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING incident_id",
                Long.class, req.type(), severity, title, detail, containerNo, req.orderId(), hubId,
                actor.getAccountId(), actor.getUserId());
        return jdbc.queryForMap("SELECT " + COLS + " FROM ops_incidents WHERE incident_id = ?", id);
    }

    @Transactional
    public Map<String, Object> update(Authentication authentication, Long id, UpdateRequest req) {
        Account actor = hubScopePolicy.currentAccount(authentication);
        UserType type = actor.getUserType();
        if (type != UserType.ADMIN && type != UserType.CONTROL && type != UserType.HUB_OP) {
            throw new AccessDeniedException("예외 · 사고를 처리할 권한이 없습니다.");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT hub_id, status, assignee, resolution FROM ops_incidents WHERE incident_id = ? FOR UPDATE", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("접수 건을 찾을 수 없습니다: " + id);
        }
        Map<String, Object> row = rows.get(0);
        Object rowHub = row.get("hub_id");
        if (type == UserType.HUB_OP && (rowHub == null || !actor.getHubId().equals(((Number) rowHub).longValue()))) {
            throw new AccessDeniedException("소속 허브의 접수 건만 처리할 수 있습니다.");
        }
        String current = String.valueOf(row.get("status"));
        String status = req.status() == null || req.status().isBlank() ? current : req.status();
        if (!STATUSES.contains(status)) {
            throw new IllegalArgumentException("알 수 없는 상태입니다: " + status);
        }
        if ("RESOLVED".equals(current) && !"RESOLVED".equals(status) && type != UserType.ADMIN) {
            throw new AccessDeniedException("해결된 건을 다시 여는 것은 관리자만 할 수 있습니다.");
        }
        String assignee = req.assignee() == null ? (String) row.get("assignee") : req.assignee().trim();
        if (assignee != null && assignee.length() > 50) {
            throw new IllegalArgumentException("담당자는 50자 이하입니다.");
        }
        String resolution = req.resolution() == null ? (String) row.get("resolution") : req.resolution().trim();
        if ("RESOLVED".equals(status) && (resolution == null || resolution.isBlank())) {
            throw new IllegalArgumentException("해결 처리에는 조치 내용이 필요합니다.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("UPDATE ops_incidents SET status = ?, assignee = ?, resolution = ?, updated_at = ?, "
                        + "resolved_at = CASE WHEN ? = 'RESOLVED' THEN COALESCE(resolved_at, ?) ELSE NULL END WHERE incident_id = ?",
                status, blankToNull(assignee), blankToNull(resolution), now, status, now, id);
        return jdbc.queryForMap("SELECT " + COLS + " FROM ops_incidents WHERE incident_id = ?", id);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
