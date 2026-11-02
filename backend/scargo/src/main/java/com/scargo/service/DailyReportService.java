package com.scargo.service;

import com.scargo.security.HubScopePolicy;
import com.scargo.security.HubScopePolicy.HubScope;
import com.scargo.util.SimpleXlsx;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 26.10.24 일일 처리량 보고서 (하루 = 한국 시각 00:00 ~ 24:00)
 *  ADMIN · CONTROL: 전체 허브 + 게이트 · 과적 검사 포함 / HUB_OP: 소속 허브 항목만
 *  JSON(화면) 과 .xlsx(내려받기) 가 같은 집계를 쓴다. 읽기 전용이라 데이터는 바꾸지 않는다.
 */
@Service
@RequiredArgsConstructor
public class DailyReportService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final JdbcTemplate jdbc;
    private final HubScopePolicy hubScopePolicy;

    public record Line(String section, String label, long count) { }

    @Transactional(readOnly = true)
    public Map<String, Object> report(Authentication authentication, LocalDate date) {
        return report(authentication, date, null);
    }

    // 26.10.30 기간 보고: date ~ last(포함). last 가 없으면 하루. 주간 · 월간 · 연간은 화면에서 시작일 · 끝일을 계산해 보낸다
    @Transactional(readOnly = true)
    public Map<String, Object> report(Authentication authentication, LocalDate date, LocalDate last) {
        HubScope scope = hubScopePolicy.view(authentication);
        LocalDate day = date == null ? LocalDate.now(KST) : date;
        LocalDate lastDay = last == null || last.isBefore(day) ? day : last;
        OffsetDateTime from = day.atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime to = lastDay.plusDays(1).atStartOfDay(KST).toOffsetDateTime();
        Long hub = scope.hubId();

        List<Line> lines = new ArrayList<>();
        if (scope.allHubs()) {
            lines.add(new Line("게이트", "통과 기록", count("SELECT COUNT(*) FROM gate_logs WHERE pass_at >= ? AND pass_at < ?", from, to)));
            lines.add(new Line("게이트", "번호판 인식 성공", count(
                    "SELECT COUNT(*) FROM gate_logs WHERE pass_at >= ? AND pass_at < ? AND recognition_status = 'SUCCESS'", from, to)));
            lines.add(new Line("과적 검사", "검사 건수", count("SELECT COUNT(*) FROM overload_checks WHERE checked_at >= ? AND checked_at < ?", from, to)));
            lines.add(new Line("과적 검사", "위반 건수", count(
                    "SELECT COUNT(*) FROM overload_checks WHERE checked_at >= ? AND checked_at < ? AND is_violation", from, to)));
        }
        String oh = hub == null ? "" : " AND dest_hub_id = ?";
        lines.add(new Line("운송", "오더 생성", orders("created_at", oh, hub, from, to)));
        lines.add(new Line("운송", "운송 출발", orders("departed_at", oh, hub, from, to)));
        lines.add(new Line("운송", "허브 도착", orders("arrived_at", oh, hub, from, to)));
        lines.add(new Line("운송", "인계 완료", orders("handed_over_at", oh, hub, from, to)));

        String rh = hub == null ? "" : " AND hub_id = ?";
        lines.add(new Line("허브 작업", "입고 접수", receipts("arrived_at", rh, hub, from, to)));
        lines.add(new Line("허브 작업", "하역 완료", receipts("unloaded_at", rh, hub, from, to)));
        lines.add(new Line("허브 작업", "현재 하역 대기·진행 중", openReceipts(rh, hub)));

        String ih = hub == null ? "" : " AND hub_id = ?";
        lines.add(new Line("컨테이너 점검", "점검 확정", inspections(null, ih, hub, from, to)));
        lines.add(new Line("컨테이너 점검", "  합격(PASS)", inspections("PASS", ih, hub, from, to)));
        lines.add(new Line("컨테이너 점검", "  불합격(FAIL)", inspections("FAIL", ih, hub, from, to)));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Line l : lines) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("section", l.section());
            m.put("label", l.label());
            m.put("count", l.count());
            rows.add(m);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", day.toString());
        result.put("to", lastDay.toString());
        result.put("scope", hub == null ? "전체 허브" : "소속 허브 " + hub);
        result.put("generatedAt", OffsetDateTime.now(KST).toString());
        result.put("rows", rows);
        return result;
    }

    @SuppressWarnings("unchecked")
    public byte[] toXlsx(Map<String, Object> report) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) report.get("rows");
        List<List<Object>> sheet = new ArrayList<>();
        sheet.add(new ArrayList<>(List.of("구분", "항목", "건수")));
        for (Map<String, Object> r : rows) {
            List<Object> line = new ArrayList<>();
            line.add(r.get("section"));
            line.add(String.valueOf(r.get("label")).trim());
            line.add(r.get("count"));
            sheet.add(line);
        }
        List<List<Object>> info = new ArrayList<>();
        info.add(new ArrayList<>(List.of("항목", "값")));
        info.add(new ArrayList<>(List.of("보고 일자", String.valueOf(report.get("date")))));
        info.add(new ArrayList<>(List.of("범위", String.valueOf(report.get("scope")))));
        info.add(new ArrayList<>(List.of("생성 시각", String.valueOf(report.get("generatedAt")))));
        return new SimpleXlsx().addSheet("일일 처리량", sheet).addSheet("보고 정보", info).toBytes();
    }

    // ── 집계 도우미 ──────────────────────────────────────

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private long orders(String column, String hubClause, Long hub, OffsetDateTime from, OffsetDateTime to) {
        String sql = "SELECT COUNT(*) FROM transport_orders WHERE " + column + " >= ? AND " + column + " < ?" + hubClause;
        return hub == null ? count(sql, from, to) : count(sql, from, to, hub);
    }

    private long receipts(String column, String hubClause, Long hub, OffsetDateTime from, OffsetDateTime to) {
        String sql = "SELECT COUNT(*) FROM hub_inbound_receipts WHERE " + column + " >= ? AND " + column + " < ?" + hubClause;
        return hub == null ? count(sql, from, to) : count(sql, from, to, hub);
    }

    private long openReceipts(String hubClause, Long hub) {
        String sql = "SELECT COUNT(*) FROM hub_inbound_receipts WHERE status IN ('ARRIVED', 'UNLOADING')" + hubClause;
        return hub == null ? count(sql) : count(sql, hub);
    }

    private long inspections(String overall, String hubClause, Long hub, OffsetDateTime from, OffsetDateTime to) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM container_inspections "
                + "WHERE record_status = 'FINALIZED' AND finalized_at >= ? AND finalized_at < ?");
        List<Object> args = new ArrayList<>();
        args.add(from);
        args.add(to);
        if (overall != null) {
            sql.append(" AND overall_status = ?");
            args.add(overall);
        }
        sql.append(hubClause);
        if (hub != null) {
            args.add(hub);
        }
        return count(sql.toString(), args.toArray());
    }

    // ── 26.10.31 확정 도장: 작성 확정 → 승인 두 단계로 이름 · 시각을 남긴다 (엑셀 결재란에 그대로 찍힘)
    //  report_signs 테이블은 처음 쓸 때 스스로 만든다. 키 = 보고 범위(전체/허브) + 기간
    private volatile boolean signReady;

    private void ensureSignTable() {
        if (signReady) return;
        jdbc.execute("CREATE TABLE IF NOT EXISTS report_signs (sign_key VARCHAR(120) PRIMARY KEY, prepared_by VARCHAR(60), prepared_at TIMESTAMPTZ, approved_by VARCHAR(60), approved_at TIMESTAMPTZ)");
        signReady = true;
    }

    private String signKey(HubScope scope, LocalDate date, LocalDate to) {
        LocalDate day = date == null ? LocalDate.now(KST) : date;
        LocalDate last = to == null || to.isBefore(day) ? day : to;
        return "daily|" + (scope.hubId() == null ? "ALL" : scope.hubId()) + "|" + day + "|" + last;
    }

    private Map<String, Object> signState(String key, HubScope scope) {
        List<Map<String, Object>> r = jdbc.queryForList(
                "SELECT prepared_by, to_char(prepared_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD HH24:MI') AS prepared_at, approved_by, "
                        + "to_char(approved_at AT TIME ZONE 'Asia/Seoul', 'YYYY-MM-DD HH24:MI') AS approved_at FROM report_signs WHERE sign_key = ?", key);
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> row = r.isEmpty() ? Map.of() : r.get(0);
        m.put("preparedBy", row.get("prepared_by"));
        m.put("preparedAt", row.get("prepared_at"));
        m.put("approvedBy", row.get("approved_by"));
        m.put("approvedAt", row.get("approved_at"));
        m.put("locked", row.get("approved_by") != null);
        m.put("canApprove", scope.userType() == com.scargo.entity.Account.UserType.ADMIN || scope.userType() == com.scargo.entity.Account.UserType.CONTROL);
        m.put("canReset", scope.userType() == com.scargo.entity.Account.UserType.ADMIN);
        return m;
    }

    @Transactional
    public Map<String, Object> sign(Authentication authentication, LocalDate date, LocalDate to) {
        HubScope scope = hubScopePolicy.view(authentication);
        ensureSignTable();
        return signState(signKey(scope, date, to), scope);
    }

    /** 작성 확정: 로그인한 사람 이름 · 지금 시각을 작성란에 남긴다. 이미 승인됐으면 막는다 */
    @Transactional
    public Map<String, Object> prepare(Authentication authentication, LocalDate date, LocalDate to) {
        HubScope scope = hubScopePolicy.view(authentication);
        ensureSignTable();
        String key = signKey(scope, date, to);
        if (Boolean.TRUE.equals(signState(key, scope).get("locked"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 승인된 보고서입니다.");
        String name = hubScopePolicy.currentAccount(authentication).getUserName();
        jdbc.update("INSERT INTO report_signs(sign_key, prepared_by, prepared_at) VALUES (?, ?, now()) "
                + "ON CONFLICT (sign_key) DO UPDATE SET prepared_by = EXCLUDED.prepared_by, prepared_at = EXCLUDED.prepared_at", key, name);
        return signState(key, scope);
    }

    /** 승인: 관리자(ADMIN) · 관제(CONTROL) 만. 작성 확정이 먼저 있어야 한다 */
    @Transactional
    public Map<String, Object> approve(Authentication authentication, LocalDate date, LocalDate to) {
        HubScope scope = hubScopePolicy.view(authentication);
        ensureSignTable();
        String key = signKey(scope, date, to);
        Map<String, Object> st = signState(key, scope);
        if (!Boolean.TRUE.equals(st.get("canApprove"))) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "승인은 관리자 · 관제만 할 수 있습니다.");
        if (st.get("preparedBy") == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "작성 확정이 먼저 필요합니다.");
        if (Boolean.TRUE.equals(st.get("locked"))) return st;
        jdbc.update("UPDATE report_signs SET approved_by = ?, approved_at = now() WHERE sign_key = ?",
                hubScopePolicy.currentAccount(authentication).getUserName(), key);
        return signState(key, scope);
    }

    /** 되돌리기 (시연 반복용): 관리자(ADMIN) 만 */
    @Transactional
    public Map<String, Object> resetSign(Authentication authentication, LocalDate date, LocalDate to) {
        HubScope scope = hubScopePolicy.view(authentication);
        ensureSignTable();
        if (scope.userType() != com.scargo.entity.Account.UserType.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "되돌리기는 관리자만 할 수 있습니다.");
        String key = signKey(scope, date, to);
        jdbc.update("DELETE FROM report_signs WHERE sign_key = ?", key);
        return signState(key, scope);
    }
}
