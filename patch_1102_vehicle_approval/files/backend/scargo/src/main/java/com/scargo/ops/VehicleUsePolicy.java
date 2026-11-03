package com.scargo.ops;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 26.11.02 차량 사용 정책 (허브 게이트 입차 · 배차 · 운송오더 공통)
 *  - 진입 허가(trucks.entry_approval)는 차량 자체 심사. 기사 배정(trucks.assigned_account_id)과 따로 움직인다
 *    → '허가 + 기사 미배정'은 정상(배차 대기). 여기서는 허가 상태를 바꾸지 않고 읽기만 한다
 *  - 게이트 입차: S카고 등록 차량(trucks)이고 APPROVED 여야 통과. 레인(화물차/택배차)도 trucks.vehicle_category 기준 (hub_vehicles 는 판정에 안 씀)
 *  - 배차: APPROVED 차량 + 그 배차에 지정된 유효 기사(승인 · 같은 운송사 · 차량 구분에 맞는 유형)
 */
@Component
@RequiredArgsConstructor
public class VehicleUsePolicy {

    private final JdbcTemplate jdbc;

    /** trucks 한 대 (번호는 공백 무시로 찾는다) */
    public record TruckInfo(String vehicleNo, Long companyId, String category, String entryApproval) {}

    /** 배차 기사 (accounts) */
    public record DriverInfo(Long accountId, Long companyId, String userType, String approvalStatus) {}

    /** 게이트 입차 판정: decision ALLOW/BLOCK, blockReason UNREADABLE/UNREGISTERED/PENDING/REJECTED (허용이면 null), kind CARGO/COURIER/UNKNOWN */
    public record GateEntry(String decision, String blockReason, String kind) {
        public boolean allowed() { return "ALLOW".equals(decision); }
    }

    public TruckInfo truck(String plate) {
        String p = plate == null ? null : plate.replace(" ", "").trim();
        if (p == null || p.isEmpty()) return null;
        List<Map<String, Object>> r = jdbc.queryForList(
                "SELECT vehicle_no, company_id, vehicle_category, entry_approval FROM trucks WHERE REPLACE(vehicle_no, ' ', '') = ? LIMIT 1", p);
        if (r.isEmpty()) return null;
        Map<String, Object> m = r.get(0);
        return new TruckInfo((String) m.get("vehicle_no"), m.get("company_id") == null ? null : ((Number) m.get("company_id")).longValue(),
                (String) m.get("vehicle_category"), (String) m.get("entry_approval"));
    }

    public DriverInfo driver(Long accountId) {
        if (accountId == null) return null;
        List<Map<String, Object>> r = jdbc.queryForList("SELECT company_id, user_type, approval_status FROM accounts WHERE account_id = ?", accountId);
        if (r.isEmpty()) return null;
        Map<String, Object> m = r.get(0);
        return new DriverInfo(accountId, m.get("company_id") == null ? null : ((Number) m.get("company_id")).longValue(),
                (String) m.get("user_type"), (String) m.get("approval_status"));
    }

    // ── 게이트 ──

    /** 허브 레인 구분은 S카고 차량 구분(trucks.vehicle_category) 하나로 정한다: CONTAINER_TRUCK → 화물차(입고), GENERAL_TRUCK → 택배차(출고).
     *  구분이 비어 있으면 스캔한 레인. hub_vehicles.vehicle_kind 는 판정에 쓰지 않는다 (26.11.02 통일) */
    public static String kindOf(String category, String laneKind) {
        if ("CONTAINER_TRUCK".equals(category)) return "CARGO";
        if ("GENERAL_TRUCK".equals(category)) return "COURIER";
        return laneKind == null ? "UNKNOWN" : laneKind;
    }

    /** 입차 판정 (DB 없이 계산 → 테스트 가능). plate 가 null 이면 판독 불가 */
    public static GateEntry gateEntry(String plate, TruckInfo t, String laneKind) {
        if (plate == null || plate.isBlank()) return new GateEntry("BLOCK", "UNREADABLE", "UNKNOWN");
        if (t == null) return new GateEntry("BLOCK", "UNREGISTERED", "UNKNOWN"); // hub_vehicles 에만 있는 번호도 S카고 미등록이면 차단
        String kind = kindOf(t.category(), laneKind);
        if ("APPROVED".equals(t.entryApproval())) return new GateEntry("ALLOW", null, kind);
        return new GateEntry("BLOCK", "REJECTED".equals(t.entryApproval()) ? "REJECTED" : "PENDING", kind);
    }

    public static String reasonText(String reason) {
        if (reason == null) return null;
        return switch (reason) {
            case "UNREADABLE" -> "번호판 판독 불가";
            case "UNREGISTERED" -> "미등록 차량";
            case "PENDING" -> "진입 허가 심사대기";
            case "REJECTED" -> "진입 불허 차량";
            default -> reason;
        };
    }

    // ── 배차 ──

    /** 배차 불가 사유 (null = 가능). company 가 null 이면 소속 확인 생략(관리자·시뮬레이터) */
    public static String dispatchProblem(TruckInfo t, DriverInfo d, Long company) {
        if (t == null) return "등록되지 않은 차량입니다.";
        if (company != null && !Objects.equals(company, t.companyId())) return "우리 운송사 차량이 아닙니다.";
        if (!"APPROVED".equals(t.entryApproval()))
            return "진입 허가되지 않은 차량은 배차할 수 없습니다 (" + t.vehicleNo() + " · " + ("REJECTED".equals(t.entryApproval()) ? "불허" : "심사대기") + ").";
        if (d == null) return "배차할 기사를 지정하세요.";
        if (!"CONTAINER_DRIVER".equals(d.userType()) && !"BRANCH_DRIVER".equals(d.userType())) return "운송기사 계정이 아닙니다.";
        if (!"APPROVED".equals(d.approvalStatus())) return "가입 승인된 기사만 배차할 수 있습니다.";
        if (!Objects.equals(d.companyId(), t.companyId())) return "차량과 기사의 소속 운송사가 다릅니다.";
        // TruckService.validateDriverForTruck 와 같은 규칙 (구분 미등록이면 두 유형 모두 허용)
        if ("CONTAINER_TRUCK".equals(t.category()) && !"CONTAINER_DRIVER".equals(d.userType())) return "컨테이너 트랙터에는 컨테이너 운송기사만 배차할 수 있습니다.";
        if ("GENERAL_TRUCK".equals(t.category()) && !"BRANCH_DRIVER".equals(d.userType())) return "일반 화물차에는 지선 운송기사만 배차할 수 있습니다.";
        return null;
    }

    /** 배차 · 차량/기사 변경 전에 서버에서 확인. 다른 운송사 차량은 403, 나머지는 400 */
    public void requireDispatchable(String vehicleNo, Long driverId, Long company, String driverType) {
        TruckInfo t = truck(vehicleNo);
        DriverInfo d = driver(driverId);
        String p = dispatchProblem(t, d, company);
        if (p == null && driverType != null && !driverType.equals(d.userType()))
            p = "CONTAINER_DRIVER".equals(driverType) ? "컨테이너 운송기사만 지정할 수 있습니다." : "지선 운송기사만 지정할 수 있습니다.";
        if (p == null) return;
        if (t != null && company != null && !Objects.equals(company, t.companyId())) throw new AccessDeniedException(p);
        if (d != null && company != null && !Objects.equals(company, d.companyId())) throw new AccessDeniedException("다른 운송사 기사입니다.");
        throw new IllegalArgumentException(p);
    }
}
