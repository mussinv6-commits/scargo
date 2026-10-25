package com.scargo.service;

import com.scargo.entity.Account;
import com.scargo.security.HubScopePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 26.10.23 지역센터 인수 확정 (마이그레이션 14장 RECEIVED · received_at, 팀 DB v16 fix_1440)
 *
 * 지선(허브 → 지역센터, shipment_legs.leg_type = HUB_TO_CENTER) 운행이 센터에 도착(ARRIVED)한 뒤
 * 센터가 물량을 넘겨받았음을 확정하면 RECEIVED + received_at 을 기록한다.
 *
 *   권한 (승인 명세 "권한별 관리 구간": CENTER_OP = 소속 센터의 간선 도착 ~ 인수 확정,
 *         ADMIN = 운영 업무 없음 · 운영 처리 권한 자동 부여 안 함, CONTROL = 지역센터 도착 전까지)
 *   조회: ADMIN(전체 센터 · 조회만), CONTROL(도착 전 IN_TRANSIT 만 — TruckAccessPolicy 의 관제 범위와 같음), CENTER_OP(소속 센터)
 *   확정: CENTER_OP(소속 센터)만
 *   상태: ARRIVED → RECEIVED 만 (이미 RECEIVED 면 409, 도착 전 · 취소면 409). 되돌리기 없음
 *   26.10.23 확정자: received_by_account_id = 확정한 CENTER_OP (마이그레이션 15장). 15장 이전 RECEIVED 는 확정자 없음(NULL) 그대로
 *   ARRIVED 를 만드는 기존 흐름(S-Bot 도착 처리 · 시뮬레이터)과 항만 → 허브 구간은 바꾸지 않는다
 */
@Service
@RequiredArgsConstructor
public class CenterReceiptService {

    static final Set<String> LISTED = Set.of("IN_TRANSIT", "ARRIVED", "RECEIVED");

    private final JdbcTemplate jdbc;
    private final HubScopePolicy hubScopePolicy;

    private static final String SELECT = "SELECT l.assignment_id AS \"assignmentId\", l.vehicle_no AS \"vehicleNo\", l.status, "
            + "l.from_center_id AS \"fromCenterId\", fc.center_name AS \"fromCenterName\", "
            + "l.to_center_id AS \"toCenterId\", tc.center_name AS \"toCenterName\", "
            + "l.departed_at AS \"departedAt\", l.arrived_at AS \"arrivedAt\", l.received_at AS \"receivedAt\", "
            + "ra.user_id AS \"receivedByUserId\", "
            + "(SELECT COALESCE(sum(d.parcel_count), 0) FROM hub_dispatches d WHERE d.assignment_id = l.assignment_id) AS \"parcelCount\" "
            + "FROM shipment_legs l JOIN logistics_centers fc ON fc.center_id = l.from_center_id "
            + "JOIN logistics_centers tc ON tc.center_id = l.to_center_id "
            + "LEFT JOIN accounts ra ON ra.account_id = l.received_by_account_id "
            + "WHERE l.leg_type = 'HUB_TO_CENTER' ";

    /** 내 범위의 지선 운행 (status: IN_TRANSIT · ARRIVED · RECEIVED, 비우면 셋 다) */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Authentication authentication, String status) {
        Account actor = viewer(authentication);
        StringBuilder sql = new StringBuilder(SELECT);
        List<Object> args = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            String s = status.trim().toUpperCase();
            if (!LISTED.contains(s)) throw new IllegalArgumentException("조회할 수 없는 상태입니다: " + status);
            sql.append("AND l.status = ? ");
            args.add(s);
        } else {
            sql.append("AND l.status IN ('IN_TRANSIT', 'ARRIVED', 'RECEIVED') ");
        }
        if (actor.getUserType() == Account.UserType.CENTER_OP) {
            sql.append("AND l.to_center_id = ? ");
            args.add(actor.getCenterId());
        }
        if (actor.getUserType() == Account.UserType.CONTROL) {
            // 통합 관제사의 범위는 지역센터 도착 전까지 (도착 · 인수 단계는 지역센터 관제사 업무)
            if (status != null && !status.isBlank() && !"IN_TRANSIT".equalsIgnoreCase(status.trim())) {
                throw new AccessDeniedException("통합 관제사는 지역센터 도착 전(운송 중) 운행만 볼 수 있습니다.");
            }
            sql.append("AND l.status = 'IN_TRANSIT' ");
        }
        sql.append("ORDER BY (l.status = 'RECEIVED'), l.arrived_at DESC NULLS LAST, l.assignment_id DESC LIMIT 300");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 센터 인수 확정: ARRIVED → RECEIVED */
    @Transactional
    public Map<String, Object> receive(Authentication authentication, Long assignmentId) {
        Account actor = viewer(authentication);
        if (actor.getUserType() != Account.UserType.CENTER_OP) {
            throw new AccessDeniedException("센터 인수 확정은 소속 센터의 지역센터 관제사만 할 수 있습니다.");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT leg_type, status, to_center_id FROM shipment_legs WHERE assignment_id = ? FOR UPDATE", assignmentId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "운송 구간이 없습니다: " + assignmentId);
        }
        Map<String, Object> leg = rows.get(0);
        Long toCenter = ((Number) leg.get("to_center_id")).longValue();
        if (!toCenter.equals(actor.getCenterId())) {
            throw new AccessDeniedException("소속 센터로 온 운행만 인수 확정할 수 있습니다.");
        }
        if (!"HUB_TO_CENTER".equals(leg.get("leg_type"))) {
            throw new IllegalStateException("지선(허브 → 지역센터) 운행만 센터 인수 확정을 합니다.");
        }
        String status = String.valueOf(leg.get("status"));
        if ("RECEIVED".equals(status)) {
            throw new IllegalStateException("이미 센터 인수가 확정된 운행입니다: " + assignmentId);
        }
        if (!"ARRIVED".equals(status)) {
            throw new IllegalStateException("센터 도착(ARRIVED) 뒤에만 인수 확정할 수 있습니다 (현재 " + status + ")");
        }
        OffsetDateTime now = OffsetDateTime.now();
        // 26.10.23 마이그레이션 15장: 확정자 = 로그인한 CENTER_OP 계정 (요청 값은 받지 않음)
        jdbc.update("UPDATE shipment_legs SET status = 'RECEIVED', received_at = ?, received_by_account_id = ? "
                + "WHERE assignment_id = ? AND status = 'ARRIVED'", now, actor.getAccountId(), assignmentId);
        return jdbc.queryForList(SELECT + "AND l.assignment_id = ?", assignmentId).get(0);
    }

    // ADMIN · CONTROL · 소속 센터가 있는 CENTER_OP 만 (계정은 DB 에서 다시 읽음)
    private Account viewer(Authentication authentication) {
        Account account = hubScopePolicy.currentAccount(authentication);
        Account.UserType type = account.getUserType();
        if (type == Account.UserType.ADMIN || type == Account.UserType.CONTROL) return account;
        if (type == Account.UserType.CENTER_OP && account.getCenterId() != null) return account;
        throw new AccessDeniedException("지역센터 인수 권한이 없습니다.");
    }
}
