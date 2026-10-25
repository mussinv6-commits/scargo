package com.scargo.security;

import com.scargo.entity.Account;
import com.scargo.entity.ContainerInspection;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 26.10.23 컨테이너 점검표 접근 범위
 *
 *   ADMIN               전체
 *   CONTROL             전체 (조회만)
 *   CORPORATE_APPROVED  company_id = 자기 업체
 *   CONTAINER_DRIVER    driver_account_id = 본인
 *   HUB_OP              허브 근거 ①②③ 중 하나 (소속 허브는 accounts.hub_id 를 DB 에서 다시 읽음)
 *                        ① 연결 오더의 도착 허브 = 소속 허브
 *                        ② 연결 오더의 운송 구간에 대한 소속 허브 입고 기록(hub_inbound_receipts)이 있음
 *                        ③ 점검표 hub_id = 소속 허브 (생성 시 서버가 ①이나 ②로 검증해 저장한 스냅샷)
 *   그 외 (CENTER_OP 등)  접근 불가
 *
 * HUB_OP 업무(생성 · 수정 · DRAFT 취소 · 재점검)는 소속 허브 도착이 확인된 뒤 인계 전에만:
 *   소속 허브 입고 기록 있음 + 오더가 HANDED_OVER · CANCELED 아님 + handovers 에 그 오더 기록 없음
 * 컨테이너 번호나 시각만으로는 허브 소속을 정하지 않는다 (오더 · 운송 구간 · 입고 기록으로만)
 */
@Component
@RequiredArgsConstructor
public class ContainerInspectionAccessPolicy {

    // R2 자동 연결 대상 진행 중 오더 (CREATED 제외)
    public static final Set<String> LINKABLE_ORDER_STATUSES = Set.of("ASSIGNED", "LOADED", "GATE_OUT", "IN_TRANSIT", "ARRIVED");

    private static final String HUB_EVIDENCE =
            "(ci.hub_id = ? "
            + " OR ci.order_id IN (SELECT o.order_id FROM transport_orders o WHERE o.dest_hub_id = ?) "
            + " OR ci.order_id IN (SELECT o.order_id FROM transport_orders o "
            + "                    JOIN hub_inbound_receipts r ON r.assignment_id = o.assignment_id WHERE r.hub_id = ?))";

    private final HubScopePolicy hubScopePolicy;
    private final JdbcTemplate jdbc;

    public record OrderInfo(Long orderId, String orderNo, String status, Long companyId, Long destHubId,
                            Long assignmentId, String containerNo, String vehicleNo, Long driverAccountId,
                            OffsetDateTime assignedAt) {
    }

    /** 점검표를 다룰 수 있는 계정 (DB 에서 다시 읽음). 역할 · 소속이 맞지 않으면 403 */
    public Account actor(Authentication authentication) {
        Account account = hubScopePolicy.currentAccount(authentication);
        Account.UserType type = account.getUserType();
        if (type == Account.UserType.ADMIN || type == Account.UserType.CONTROL || type == Account.UserType.CONTAINER_DRIVER) {
            return account;
        }
        if (type == Account.UserType.CORPORATE_APPROVED && account.getCompanyId() != null) {
            return account;
        }
        if (type == Account.UserType.HUB_OP && account.getHubId() != null) {
            return account;
        }
        throw new AccessDeniedException("컨테이너 점검표 권한이 없습니다.");
    }

    public static boolean is(Account account, Account.UserType type) {
        return account.getUserType() == type;
    }

    // ── 조회 범위 ─────────────────────────────────────────

    public boolean canView(Account account, ContainerInspection c) {
        switch (account.getUserType()) {
            case ADMIN:
            case CONTROL:
                return true;
            case CORPORATE_APPROVED:
                return account.getCompanyId().equals(c.getCompanyId());
            case CONTAINER_DRIVER:
                return account.getAccountId().equals(c.getDriverAccountId());
            case HUB_OP:
                Long hub = account.getHubId();
                Long n = jdbc.queryForObject("SELECT COUNT(*) FROM container_inspections ci WHERE ci.inspection_id = ? AND "
                        + HUB_EVIDENCE, Long.class, c.getInspectionId(), hub, hub, hub);
                return n != null && n > 0;
            default:
                return false;
        }
    }

    public void requireView(Account account, ContainerInspection c) {
        if (!canView(account, c)) {
            throw new AccessDeniedException("조회 가능한 범위의 점검 기록이 아닙니다.");
        }
    }

    /** 내 범위의 점검 기록 번호 (최근 작성 순, 최대 500건) */
    public List<Long> visibleIds(Account account, String containerNo, Long orderId, String recordStatus) {
        StringBuilder sql = new StringBuilder("SELECT ci.inspection_id FROM container_inspections ci WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();
        switch (account.getUserType()) {
            case ADMIN:
            case CONTROL:
                break;
            case CORPORATE_APPROVED:
                sql.append("AND ci.company_id = ? ");
                args.add(account.getCompanyId());
                break;
            case CONTAINER_DRIVER:
                sql.append("AND ci.driver_account_id = ? ");
                args.add(account.getAccountId());
                break;
            case HUB_OP:
                sql.append("AND ").append(HUB_EVIDENCE).append(' ');
                args.add(account.getHubId());
                args.add(account.getHubId());
                args.add(account.getHubId());
                break;
            default:
                throw new AccessDeniedException("컨테이너 점검표 권한이 없습니다.");
        }
        if (containerNo != null && !containerNo.isBlank()) {
            sql.append("AND ci.container_no = ? ");
            args.add(containerNo.trim().toUpperCase());
        }
        if (orderId != null) {
            sql.append("AND ci.order_id = ? ");
            args.add(orderId);
        }
        if (recordStatus != null && !recordStatus.isBlank()) {
            sql.append("AND ci.record_status = ? ");
            args.add(recordStatus.trim().toUpperCase());
        }
        sql.append("ORDER BY ci.created_at DESC, ci.inspection_id DESC LIMIT 500");
        return jdbc.queryForList(sql.toString(), Long.class, args.toArray());
    }

    // ── 오더 ─────────────────────────────────────────────

    public Optional<OrderInfo> order(Long orderId) {
        if (orderId == null) return Optional.empty();
        return jdbc.query("SELECT order_id, order_no, status, company_id, dest_hub_id, assignment_id, container_no, vehicle_no, "
                        + "driver_account_id, assigned_at FROM transport_orders WHERE order_id = ?",
                (rs, i) -> new OrderInfo(rs.getLong("order_id"), rs.getString("order_no"), rs.getString("status"),
                        rs.getLong("company_id"), rs.getLong("dest_hub_id"), (Long) rs.getObject("assignment_id"),
                        rs.getString("container_no"), rs.getString("vehicle_no"), (Long) rs.getObject("driver_account_id"),
                        rs.getObject("assigned_at", OffsetDateTime.class)),
                orderId).stream().findFirst();
    }

    public Optional<String> orderNo(Long orderId) {
        return order(orderId).map(OrderInfo::orderNo);
    }

    /** R2: 이 컨테이너의 진행 중 오더 (ASSIGNED · LOADED · GATE_OUT · IN_TRANSIT · ARRIVED) */
    public List<Long> linkableOrderIds(String containerNo) {
        return jdbc.queryForList("SELECT order_id FROM transport_orders WHERE container_no = ? "
                + "AND status IN ('ASSIGNED', 'LOADED', 'GATE_OUT', 'IN_TRANSIT', 'ARRIVED') ORDER BY order_id", Long.class, containerNo);
    }

    // ── HUB_OP 업무 단계 (K2) ─────────────────────────────

    /** 소속 허브 도착 확인 뒤 · 인계 전인지. 아니면 403 */
    public void requireHubWork(Account account, Long orderId) {
        if (!hubWorkAllowed(account, orderId)) {
            throw new AccessDeniedException("허브 관제사는 소속 허브 도착이 확인된 뒤 인계 전까지만 점검표를 작성 · 수정할 수 있습니다.");
        }
    }

    public boolean hubWorkAllowed(Account account, Long orderId) {
        if (account.getHubId() == null || orderId == null) return false;
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM transport_orders o "
                        + "JOIN hub_inbound_receipts r ON r.assignment_id = o.assignment_id AND r.hub_id = ? "
                        + "WHERE o.order_id = ? AND o.dest_hub_id = ? AND o.status NOT IN ('HANDED_OVER', 'CANCELED') "
                        + "AND NOT EXISTS (SELECT 1 FROM handovers h WHERE h.order_id = o.order_id)",
                Long.class, account.getHubId(), orderId, account.getHubId());
        return n != null && n > 0;
    }

    // ── 오더 없이 만들 때 컨테이너 검증 ────────────────────

    /** 기사: 본인 배정 차량에 매핑된 컨테이너 */
    public boolean driverOwnsContainer(Account account, String containerNo) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM containers c JOIN trucks t ON t.vehicle_no = c.assigned_vehicle_no "
                + "WHERE c.container_no = ? AND t.assigned_account_id = ?", Long.class, containerNo, account.getAccountId());
        return n != null && n > 0;
    }

    /** 기사: 오더에 배정된 기사가 본인이고, 오더 차량이 본인 배정 차량 (trucks.assigned_account_id) */
    public boolean driverOwnsOrder(Account account, OrderInfo order) {
        if (order.vehicleNo() == null || !account.getAccountId().equals(order.driverAccountId())) return false;
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM trucks WHERE vehicle_no = ? AND assigned_account_id = ?",
                Long.class, order.vehicleNo(), account.getAccountId());
        return n != null && n > 0;
    }

    /** 운송사: MappingService 와 같은 기준 (자기 업체 컨테이너 또는 자기 업체 차량에 배정된 컨테이너) */
    public boolean companyOwnsContainer(Account account, String containerNo) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM containers c LEFT JOIN trucks t ON t.vehicle_no = c.assigned_vehicle_no "
                + "WHERE c.container_no = ? AND (c.company_id = ? OR t.company_id = ?)",
                Long.class, containerNo, account.getCompanyId(), account.getCompanyId());
        return n != null && n > 0;
    }
}
