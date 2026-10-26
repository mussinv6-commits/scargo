package com.scargo.ops.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 26.10.21 v11: 공컨 반납 기한 — 알림(v10) + 예외(exceptions) 동기화
 *  - 알림: CONTAINER_RETURN_DUE / CONTAINER_RETURN_OVERDUE (운송사 + 배차 기사). 같은 오더·같은 종류·같은 기한이면 1번만
 *    (반납 기한을 바꾸면 메시지의 기한이 달라져 새 기한 기준으로 다시 알림)
 *  - 예외: exception_type CONTAINER_RETURN_DUE, source = transport_orders/order_id, 오더당 1건
 *      기한까지 ops_settings.RETURN_DUE_SOON_HOURS(기본 24) 시간 이내 → WARNING, 기한 지남 → CRITICAL (같은 행을 올림)
 *      반납 완료(EMPTY_RETURNED) · 취소 · 기한 연장으로 대상이 아니게 되면 자동 RESOLVED
 *    center_id = 도착 허브 거점 → 대시보드·네트워크 거점 색에 반영
 */
@Service
@RequiredArgsConstructor
public class ReturnDueService {
    private final JdbcTemplate jdbc;

    public int dueSoonHours() {
        return jdbc.query("SELECT setting_value FROM ops_settings WHERE setting_key = 'RETURN_DUE_SOON_HOURS'", rs -> rs.next() ? rs.getInt(1) : 24);
    }

    @Transactional
    public Map<String, Integer> run() {
        int hours = dueSoonHours();
        int overdue = notify("CONTAINER_RETURN_OVERDUE", "[반납 기한 초과] ", "o.return_due_at <= now()", hours);
        int due = notify("CONTAINER_RETURN_DUE", "[반납 기한 임박] ", "o.return_due_at > now() AND o.return_due_at <= now() + make_interval(hours => ?)", hours);
        Map<String, Integer> ex = syncExceptions(null);
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("due", due); m.put("overdue", overdue); m.put("dueSoonHours", hours); m.putAll(ex);
        return m;
    }

    private static final String MSG = "o.order_no || ' · ' || o.container_no || ' · 반납 CY ' || COALESCE(o.return_cy, '-') || ' · 기한 ' || to_char(o.return_due_at AT TIME ZONE 'Asia/Seoul', 'MM/DD HH24:MI')";

    private int notify(String type, String prefix, String cond, int hours) {
        String base = """
            FROM transport_orders o
           WHERE o.return_due_at IS NOT NULL AND o.return_status IS DISTINCT FROM 'EMPTY_RETURNED' AND o.status <> 'CANCELED' AND %s
             AND NOT EXISTS (SELECT 1 FROM notifications n WHERE n.notification_type = '%s' AND n.reference_id = o.order_id AND n.message = %s)""".formatted(cond, type, MSG);
        Object[] args = type.endsWith("OVERDUE") ? new Object[0] : new Object[]{hours};
        return jdbc.update("""
            WITH t AS (SELECT o.order_id, o.company_id, o.driver_account_id, '%s' || o.container_no AS title, %s AS msg %s)
            INSERT INTO notifications(company_id, account_id, title, message, notification_type, reference_id)
            SELECT company_id, NULL, title, msg, '%s', order_id FROM t
            UNION ALL SELECT NULL, driver_account_id, title, msg, '%s', order_id FROM t WHERE driver_account_id IS NOT NULL""".formatted(prefix, MSG, base, type, type), args);
    }

    /** orderId 가 null 이면 전체, 아니면 그 오더만.
     *  fix_1300: 예외 종류 분리 — 임박 = CONTAINER_RETURN_DUE (WARNING), 초과 = CONTAINER_RETURN_OVERDUE (CRITICAL) */
    @Transactional
    public Map<String, Integer> syncExceptions(Long orderId) {
        int hours = dueSoonHours();
        String only = orderId == null ? "" : " AND o.order_id = " + orderId;
        String alive = "o.return_due_at IS NOT NULL AND o.return_status IS DISTINCT FROM 'EMPTY_RETURNED' AND o.status <> 'CANCELED'";
        int upserted = upsert("CONTAINER_RETURN_DUE", "WARNING", "공컨 반납 기한 임박 · ",
                alive + " AND o.return_due_at > now() AND o.return_due_at <= now() + make_interval(hours => " + hours + ")" + only)
                + upsert("CONTAINER_RETURN_OVERDUE", "CRITICAL", "공컨 반납 기한 초과 · ", alive + " AND o.return_due_at <= now()" + only);
        // 대상이 아니게 된 오더: 자동 해결 (임박 → 초과로 넘어가면 임박 예외는 해결, 초과 예외가 새로 열림)
        String note = """
            CASE WHEN o.return_status = 'EMPTY_RETURNED' THEN '공컨 반납 완료 (자동 해결)'
                 WHEN o.status = 'CANCELED' THEN '운송오더 취소 (자동 해결)'
                 WHEN e.exception_type = 'CONTAINER_RETURN_DUE' AND o.return_due_at <= now() THEN '반납 기한 초과 예외로 넘어감 (자동 해결)'
                 ELSE '반납 기한 변경으로 해소 (자동 해결)' END""";
        int resolved = jdbc.update("""
            UPDATE exceptions e SET status = 'RESOLVED', resolved_at = now(), resolution_note = %s
              FROM transport_orders o
             WHERE e.source_table = 'transport_orders' AND e.source_key = o.order_id::text AND e.status <> 'RESOLVED' %s
               AND ((e.exception_type = 'CONTAINER_RETURN_DUE' AND NOT (%s AND o.return_due_at > now() AND o.return_due_at <= now() + make_interval(hours => %d)))
                 OR (e.exception_type = 'CONTAINER_RETURN_OVERDUE' AND NOT (%s AND o.return_due_at <= now())))""".formatted(note, only, alive, hours, alive));
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("exceptionsUpserted", upserted); m.put("exceptionsResolved", resolved);
        return m;
    }

    /** 한 종류 예외 열기·문구 갱신. 관제사가 직접 해결한 예외는 다시 열지 않고, 자동 해결된 것만 다시 연다 */
    private int upsert(String type, String severity, String prefix, String where) {
        String reopen = "(exceptions.status = 'RESOLVED' AND exceptions.resolution_note LIKE '%(자동 해결)')";
        return jdbc.update(("""
            INSERT INTO exceptions(exception_type, severity, center_id, assignment_id, source_table, source_key, message, status, occurred_at)
            SELECT '%s', '%s', NULL, o.assignment_id, 'transport_orders', o.order_id::text, '%s' || %s, 'OPEN', now()
              FROM transport_orders o WHERE %s
            ON CONFLICT (source_table, source_key, exception_type) DO UPDATE
               SET message = EXCLUDED.message, assignment_id = EXCLUDED.assignment_id,
                   status = CASE WHEN {R} THEN 'OPEN' ELSE exceptions.status END,
                   resolved_at = CASE WHEN {R} THEN NULL ELSE exceptions.resolved_at END,
                   resolution_note = CASE WHEN {R} THEN NULL ELSE exceptions.resolution_note END,
                   occurred_at = CASE WHEN {R} THEN now() ELSE exceptions.occurred_at END
             WHERE {R} OR (exceptions.status <> 'RESOLVED' AND exceptions.message IS DISTINCT FROM EXCLUDED.message)""")
                .formatted(type, severity, prefix, MSG, where).replace("{R}", reopen));
    }
}
