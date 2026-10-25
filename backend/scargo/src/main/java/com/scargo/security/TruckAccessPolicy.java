package com.scargo.security;

import com.scargo.entity.Account;
import com.scargo.entity.Truck;
import com.scargo.repository.AccountRepository;
import com.scargo.repository.TruckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TruckAccessPolicy {

    private final AccountRepository accountRepository;
    private final TruckRepository truckRepository;
    private final JdbcTemplate jdbc;

    public Account currentAccount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedAccountPrincipal)) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
        AuthenticatedAccountPrincipal principal =
                (AuthenticatedAccountPrincipal) authentication.getPrincipal();
        return accountRepository.findById(principal.getId())
                .orElseThrow(() -> new AccessDeniedException("유효한 계정이 아닙니다."));
    }

    public List<Truck> readableTrucks(Authentication authentication, Long companyId) {
        Account account = currentAccount(authentication);
        List<Truck> trucks = companyId == null
                ? truckRepository.findAll()
                : truckRepository.findByCompany_CompanyId(companyId);
        return trucks.stream().filter(truck -> canRead(account, truck)).toList();
    }

    public void requireRead(Authentication authentication, String vehicleNo) {
        Account account = currentAccount(authentication);
        Truck truck = findTruck(vehicleNo);
        if (!canRead(account, truck)) {
            throw new AccessDeniedException("조회 가능한 관리 구간의 차량이 아닙니다.");
        }
    }

    // ADMIN은 차량 기준정보 관리, 운송사는 자기 업체 차량 준비만 허용
    public void requireCompanyManagement(Authentication authentication, Long companyId) {
        Account account = currentAccount(authentication);
        if (companyId == null) {
            throw new IllegalArgumentException("소속 업체를 지정해주세요.");
        }
        if (account.getUserType() == Account.UserType.ADMIN) {
            return;
        }
        if (account.getUserType() != Account.UserType.CORPORATE_APPROVED
                || !companyId.equals(account.getCompanyId())) {
            throw new AccessDeniedException("자기 운송사의 차량만 관리할 수 있습니다.");
        }
    }

    public void requireTruckManagement(Authentication authentication, String vehicleNo) {
        requireCompanyManagement(authentication, findTruck(vehicleNo).getCompanyId());
    }

    // 배차는 운송사 담당자 업무. ADMIN/HUB_OP의 운영 배차는 허용하지 않음
    public Long dispatchCompany(Authentication authentication) {
        Account account = currentAccount(authentication);
        if (account.getUserType() != Account.UserType.CORPORATE_APPROVED
                || account.getCompanyId() == null) {
            throw new AccessDeniedException("소속 업체가 있는 운송사 담당자만 기사 배정을 변경할 수 있습니다.");
        }
        return account.getCompanyId();
    }

    public void requireControl(Authentication authentication, String vehicleNo) {
        Account account = currentAccount(authentication);
        if (account.getUserType() != Account.UserType.CONTROL
                || !canRead(account, findTruck(vehicleNo))) {
            throw new AccessDeniedException("통합 관제사의 관리 구간에 해당하지 않습니다.");
        }
    }

    private Truck findTruck(String vehicleNo) {
        return truckRepository.findById(vehicleNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 차량입니다: " + vehicleNo));
    }

    private boolean canRead(Account account, Truck truck) {
        if (account.getUserType() == null) return false;
        String vehicleNo = truck.getVehicleNo();
        switch (account.getUserType()) {
            case ADMIN:
                return true;
            case CORPORATE_APPROVED:
                return account.getCompanyId() != null
                        && account.getCompanyId().equals(truck.getCompanyId());
            case CONTAINER_DRIVER:
            case LINEHAUL_DRIVER:
                return count("SELECT COUNT(*) FROM trucks WHERE vehicle_no = ? AND assigned_account_id = ?",
                        vehicleNo, account.getAccountId());
            case CONTROL:
                // 지역센터 도착 전까지: 간선 ARRIVED는 제외
                return count("SELECT COUNT(*) FROM shipment_legs WHERE vehicle_no = ? "
                        + "AND assignment_id = (SELECT MAX(x.assignment_id) FROM shipment_legs x WHERE x.vehicle_no = ?) "
                        + "AND (status IN ('ASSIGNED', 'IN_TRANSIT') "
                        + "OR (leg_type = 'PORT_TO_HUB' AND status = 'ARRIVED'))", vehicleNo, vehicleNo);
            case HUB_OP:
                if (account.getHubId() == null) return false;
                // 허브 입고 차량과 출고 배차를 조회. 배차 변경은 별도 메서드에서 차단
                return count("SELECT COUNT(*) FROM shipment_legs l JOIN hubs h "
                        + "ON (l.from_center_id = h.center_id OR l.to_center_id = h.center_id) "
                        + "WHERE l.vehicle_no = ? AND h.hub_id = ? "
                        + "AND l.assignment_id = (SELECT MAX(x.assignment_id) FROM shipment_legs x WHERE x.vehicle_no = l.vehicle_no) "
                        + "AND l.status IN ('ASSIGNED', 'IN_TRANSIT', 'ARRIVED')",
                        vehicleNo, account.getHubId());
            case CENTER_OP:
                if (account.getCenterId() == null) return false;
                return count("SELECT COUNT(*) FROM shipment_legs WHERE vehicle_no = ? "
                        // 26.10.23 센터 인수 확정(RECEIVED) 뒤에도 같은 차량을 계속 조회
                        + "AND to_center_id = ? AND leg_type = 'HUB_TO_CENTER' AND status IN ('ARRIVED', 'RECEIVED') "
                        + "AND assignment_id = (SELECT MAX(x.assignment_id) FROM shipment_legs x WHERE x.vehicle_no = ?)",
                        vehicleNo, account.getCenterId(), vehicleNo);
            default:
                return false;
        }
    }

    private boolean count(String sql, Object... args) {
        Long result = jdbc.queryForObject(sql, Long.class, args);
        return result != null && result > 0;
    }
}
