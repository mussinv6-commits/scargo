package com.scargo.service;

import com.scargo.entity.Account;
import com.scargo.entity.Account.UserType;
import com.scargo.security.HubScopePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 26.10.24 게이트 사전 예약 (표: gate_reservations, DB/추가_20261024_게이트예약.sql)
 *  예약: 업체(CORPORATE_APPROVED) · 기사(CONTAINER_DRIVER) 는 자기 업체로, ADMIN · CONTROL 은 업체를 지정해서 등록
 *  조회: ADMIN · CONTROL 전체 / 업체 · 기사는 자기 업체 예약만
 *  상태 변경: 취소(BOOKED→CANCELLED) 는 예약한 업체 또는 ADMIN · CONTROL, 입차 확인(CHECKED_IN) · 불참(NO_SHOW) 은 ADMIN · CONTROL 만
 */
@Service
@RequiredArgsConstructor
public class GateReservationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Set<String> DIRECTIONS = Set.of("IN", "OUT");
    private static final String COLS = "r.reservation_id AS \"reservationId\", r.company_id AS \"companyId\", "
            + "(SELECT c.company_name FROM companies c WHERE c.company_id = r.company_id) AS \"companyName\", "
            + "r.vehicle_no AS \"vehicleNo\", r.container_no AS \"containerNo\", r.direction, r.gate_id AS \"gateId\", "
            + "r.planned_at AS \"plannedAt\", r.status, r.memo, r.created_by_user_id AS \"createdBy\", r.created_at AS \"createdAt\"";

    private final JdbcTemplate jdbc;
    private final HubScopePolicy hubScopePolicy;

    public record CreateRequest(String vehicleNo, String containerNo, String direction, Long gateId,
                                String plannedAt, String memo, Long companyId) { }

    public record UpdateRequest(String status) { }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Authentication authentication, String date, String status) {
        Account actor = hubScopePolicy.currentAccount(authentication);
        UserType type = actor.getUserType();
        StringBuilder sql = new StringBuilder("SELECT " + COLS + " FROM gate_reservations r WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (type == UserType.CORPORATE_APPROVED || type == UserType.CONTAINER_DRIVER) {
            if (actor.getCompanyId() == null) {
                throw new AccessDeniedException("소속 업체가 없는 계정입니다.");
            }
            sql.append(" AND r.company_id = ?");
            args.add(actor.getCompanyId());
        } else if (type != UserType.ADMIN && type != UserType.CONTROL) {
            throw new AccessDeniedException("게이트 예약을 볼 수 있는 권한이 없습니다.");
        }
        if (date != null && !date.isBlank()) {
            LocalDate day = parseDate(date);
            sql.append(" AND r.planned_at >= ? AND r.planned_at < ?");
            args.add(day.atStartOfDay(KST).toOffsetDateTime());
            args.add(day.plusDays(1).atStartOfDay(KST).toOffsetDateTime());
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND r.status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY r.planned_at DESC LIMIT 300");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @Transactional
    public Map<String, Object> create(Authentication authentication, CreateRequest req) {
        Account actor = hubScopePolicy.currentAccount(authentication);
        UserType type = actor.getUserType();
        Long companyId;
        if (type == UserType.CORPORATE_APPROVED || type == UserType.CONTAINER_DRIVER) {
            companyId = actor.getCompanyId();
            if (companyId == null) {
                throw new AccessDeniedException("소속 업체가 없는 계정입니다.");
            }
        } else if (type == UserType.ADMIN || type == UserType.CONTROL) {
            companyId = req == null ? null : req.companyId();
        } else {
            throw new AccessDeniedException("게이트 예약을 등록할 권한이 없습니다.");
        }
        if (req == null || req.vehicleNo() == null || req.vehicleNo().isBlank()) {
            throw new IllegalArgumentException("차량 번호를 입력하세요.");
        }
        String vehicleNo = req.vehicleNo().trim();
        if (vehicleNo.length() > 20) {
            throw new IllegalArgumentException("차량 번호는 20자 이하입니다.");
        }
        String direction = req.direction() == null || req.direction().isBlank() ? "IN" : req.direction();
        if (!DIRECTIONS.contains(direction)) {
            throw new IllegalArgumentException("구분은 IN(반입) 또는 OUT(반출)입니다.");
        }
        String containerNo = req.containerNo() == null || req.containerNo().isBlank() ? null : req.containerNo().trim().toUpperCase();
        if (containerNo != null && containerNo.length() > 11) {
            throw new IllegalArgumentException("컨테이너 번호는 11자 이하입니다.");
        }
        OffsetDateTime plannedAt;
        try {
            plannedAt = OffsetDateTime.parse(req.plannedAt());
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IllegalArgumentException("예약 시각 형식이 올바르지 않습니다.");
        }
        if (plannedAt.isBefore(OffsetDateTime.now().minusMinutes(10))) {
            throw new IllegalArgumentException("지난 시각으로는 예약할 수 없습니다.");
        }
        String memo = req.memo() == null || req.memo().isBlank() ? null : req.memo().trim();
        if (memo != null && memo.length() > 300) {
            throw new IllegalArgumentException("메모는 300자 이하입니다.");
        }
        Long id;
        try {
            id = jdbc.queryForObject(
                    "INSERT INTO gate_reservations (company_id, vehicle_no, container_no, direction, gate_id, planned_at, memo, "
                            + "created_by_account_id, created_by_user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING reservation_id",
                    Long.class, companyId, vehicleNo, containerNo, direction, req.gateId(), plannedAt, memo,
                    actor.getAccountId(), actor.getUserId());
        } catch (DuplicateKeyException e) {
            throw new IllegalStateException("같은 차량이 같은 시각에 이미 예약되어 있습니다.");
        }
        return jdbc.queryForMap("SELECT " + COLS + " FROM gate_reservations r WHERE r.reservation_id = ?", id);
    }

    @Transactional
    public Map<String, Object> update(Authentication authentication, Long id, UpdateRequest req) {
        Account actor = hubScopePolicy.currentAccount(authentication);
        UserType type = actor.getUserType();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT company_id, status FROM gate_reservations WHERE reservation_id = ? FOR UPDATE", id);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("예약을 찾을 수 없습니다: " + id);
        }
        Object company = rows.get(0).get("company_id");
        String current = String.valueOf(rows.get(0).get("status"));
        String next = req == null ? null : req.status();
        if (next == null || !Set.of("CANCELLED", "CHECKED_IN", "NO_SHOW").contains(next)) {
            throw new IllegalArgumentException("상태는 CANCELLED · CHECKED_IN · NO_SHOW 중 하나입니다.");
        }
        boolean staff = type == UserType.ADMIN || type == UserType.CONTROL;
        if ("CANCELLED".equals(next)) {
            boolean owner = (type == UserType.CORPORATE_APPROVED || type == UserType.CONTAINER_DRIVER)
                    && company != null && actor.getCompanyId() != null
                    && actor.getCompanyId().equals(((Number) company).longValue());
            if (!staff && !owner) {
                throw new AccessDeniedException("본인 업체의 예약만 취소할 수 있습니다.");
            }
        } else if (!staff) {
            throw new AccessDeniedException("입차 확인 · 불참 처리는 관리자 · 관제만 할 수 있습니다.");
        }
        if (!"BOOKED".equals(current)) {
            throw new IllegalStateException("예약 상태(BOOKED)인 건만 바꿀 수 있습니다 (현재 " + current + ")");
        }
        jdbc.update("UPDATE gate_reservations SET status = ?, updated_at = now() WHERE reservation_id = ?", next, id);
        // 26.10.24 관리자 · 관제가 입차 확인 / 불참 처리하면 예약한 업체의 알림 벨로 알려 준다
        if (staff && company != null && !"CANCELLED".equals(next)) {
            Map<String, Object> r = jdbc.queryForMap("SELECT vehicle_no, planned_at FROM gate_reservations WHERE reservation_id = ?", id);
            boolean in = "CHECKED_IN".equals(next);
            jdbc.update("INSERT INTO notifications (company_id, title, message, notification_type, reference_id) VALUES (?, ?, ?, 'SYSTEM', ?)",
                    ((Number) company).longValue(),
                    in ? "[예약] 입차 확인" : "[예약] 불참 처리",
                    "예약 #" + id + " " + r.get("vehicle_no") + (in ? " 차량이 게이트에서 확인되었습니다." : " 예약이 불참으로 처리되었습니다."),
                    id);
        }
        return jdbc.queryForMap("SELECT " + COLS + " FROM gate_reservations r WHERE r.reservation_id = ?", id);
    }

    private static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("날짜 형식은 YYYY-MM-DD 입니다.");
        }
    }
}
