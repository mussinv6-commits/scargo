package com.scargo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 26.10.24 업체별 실적 (최근 N일, 읽기 전용).  오더 수 · 인계 완료 · 지연율(ETA 초과 도착) · 평균 운송 시간 · 점검 확정 · 불합격률
 *  등급: 점검 불합격률과 지연률이 모두 5% 이하 = A, 모두 15% 이하 = B, 그 외 C, 자료가 없으면 "-"
 */
@Service
@RequiredArgsConstructor
public class CompanyScoreService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> scorecard(Integer days) {
        return scorecard(days, null, null);
    }

    /**
     * 26.10.29 기간 지정: from ~ to (둘 다 있으면 그 날짜 0시 ~ to 다음날 0시 전까지, 최대 366일). 없으면 예전처럼 최근 days 일
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> scorecard(Integer days, LocalDate from, LocalDate to) {
        List<Object> params = new ArrayList<>();
        final boolean ranged = from != null && to != null;
        final Timestamp start, end;
        final int n;
        if (ranged) {
            if (to.isBefore(from)) throw new IllegalArgumentException("종료일이 시작일보다 빠릅니다.");
            if (ChronoUnit.DAYS.between(from, to) > 365) throw new IllegalArgumentException("기간은 최대 1년까지 조회할 수 있습니다.");
            start = Timestamp.valueOf(from.atStartOfDay());
            end = Timestamp.valueOf(to.plusDays(1).atStartOfDay());
            n = 0;
        } else {
            start = null;
            end = null;
            n = days == null ? 30 : Math.max(1, Math.min(180, days));
        }
        // 기간 조건 (컬럼별). 파라미터는 SQL 에 나오는 순서대로 params 에 쌓는다
        java.util.function.Function<String, String> in = (col) -> {
            if (ranged) { params.add(start); params.add(end); return col + " >= ? AND " + col + " < ?"; }
            params.add(n); return col + " >= now() - (? * interval '1 day')";
        };
        String sql = "SELECT c.company_id AS \"companyId\", c.company_name AS \"companyName\", "
                + "(SELECT COUNT(*) FROM transport_orders o WHERE o.company_id = c.company_id AND " + in.apply("o.created_at") + ") AS \"orders\", "
                + "(SELECT COUNT(*) FROM transport_orders o WHERE o.company_id = c.company_id AND o.status = 'HANDED_OVER' AND " + in.apply("o.created_at") + ") AS \"handedOver\", "
                + "(SELECT COUNT(*) FROM transport_orders o WHERE o.company_id = c.company_id AND o.arrived_at IS NOT NULL AND o.eta IS NOT NULL "
                + "   AND " + in.apply("o.created_at") + ") AS \"arrived\", "
                + "(SELECT COUNT(*) FROM transport_orders o WHERE o.company_id = c.company_id AND o.arrived_at IS NOT NULL AND o.eta IS NOT NULL AND o.arrived_at > o.eta "
                + "   AND " + in.apply("o.created_at") + ") AS \"late\", "
                + "(SELECT ROUND(AVG(EXTRACT(EPOCH FROM (o.arrived_at - o.departed_at)) / 60)::numeric, 1) FROM transport_orders o "
                + "   WHERE o.company_id = c.company_id AND o.arrived_at IS NOT NULL AND o.departed_at IS NOT NULL AND " + in.apply("o.created_at") + ") AS \"avgTransitMin\", "
                + "(SELECT COUNT(*) FROM container_inspections i WHERE i.company_id = c.company_id AND i.record_status = 'FINALIZED' "
                + "   AND " + in.apply("i.finalized_at") + ") AS \"inspections\", "
                + "(SELECT COUNT(*) FROM container_inspections i WHERE i.company_id = c.company_id AND i.record_status = 'FINALIZED' AND i.overall_status = 'FAIL' "
                + "   AND " + in.apply("i.finalized_at") + ") AS \"failed\" "
                + "FROM companies c ORDER BY 3 DESC, c.company_name";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, params.toArray());
        for (Map<String, Object> r : rows) {
            long arrived = ((Number) r.get("arrived")).longValue();
            long late = ((Number) r.get("late")).longValue();
            long insp = ((Number) r.get("inspections")).longValue();
            long failed = ((Number) r.get("failed")).longValue();
            Double lateRate = arrived == 0 ? null : Math.round(late * 1000.0 / arrived) / 10.0;
            Double failRate = insp == 0 ? null : Math.round(failed * 1000.0 / insp) / 10.0;
            r.put("lateRate", lateRate);
            r.put("failRate", failRate);
            String grade;
            if (lateRate == null && failRate == null) {
                grade = "-";
            } else {
                double worst = Math.max(lateRate == null ? 0 : lateRate, failRate == null ? 0 : failRate);
                grade = worst <= 5 ? "A" : worst <= 15 ? "B" : "C";
            }
            r.put("grade", grade);
        }
        return rows;
    }
}
