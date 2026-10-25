package com.scargo.service;

import com.scargo.security.HubScopePolicy;
import com.scargo.security.HubScopePolicy.HubScope;
import com.scargo.util.SimpleXlsx;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
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
        HubScope scope = hubScopePolicy.view(authentication);
        LocalDate day = date == null ? LocalDate.now(KST) : date;
        OffsetDateTime from = day.atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime to = from.plusDays(1);
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
}
