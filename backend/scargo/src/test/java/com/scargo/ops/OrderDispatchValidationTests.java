package com.scargo.ops;

import com.scargo.entity.Account;
import com.scargo.ops.controller.V9WebController;
import com.scargo.ops.service.BranchService;
import com.scargo.ops.service.HubOpsService;
import com.scargo.ops.service.ReturnDueService;
import com.scargo.repository.AccountRepository;
import com.scargo.security.AuthenticatedAccountPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 26.11.02 운송오더 등록 (POST /api/web/carrier/orders): 차량 · 기사를 둘 다 비우면 배차 전 초안(CREATED),
 *  하나라도 지정하면 진입 허가 차량 + 같은 운송사 승인 컨테이너 기사가 둘 다 있어야 ASSIGNED (서버에서 확인 → API 직접 호출로 우회 불가)
 */
class OrderDispatchValidationTests {
    final Map<String, Map<String, Object>> trucks = new HashMap<>();
    final Map<Long, Map<String, Object>> accounts = new HashMap<>();
    final List<String> writes = new ArrayList<>();
    V9WebController api;

    void putTruck(String no, long co, String category, String approval) {
        trucks.put(no, new HashMap<>(Map.of("vehicle_no", no, "company_id", co, "vehicle_category", category, "entry_approval", approval)));
    }

    @BeforeEach
    void setUp() {
        Answer<Object> db = inv -> {
            String m = inv.getMethod().getName(); Object[] a = inv.getArguments(); String sql = String.valueOf(a[0]);
            if (m.equals("update")) { writes.add(sql); return 1; }
            if (m.equals("queryForObject")) {
                if (sql.contains("RETURNING")) { writes.add(sql); return 100L; }
                return a[1] == Integer.class ? (Object) 0 : (Object) 1L; // 진행 중 오더 0건 · 터미널/허브 센터 1
            }
            if (m.equals("queryForList")) {
                if (a.length > 1 && a[1] instanceof Class) return List.of(); // 설정값 없음 → 기본
                Object key = a.length > 1 ? a[1] : null;
                if (sql.contains("FROM container_seals")) return List.of(Map.of("seal_id", 1L, "shipment_id", 9L, "seal_no", "SC-1001"));
                if (sql.contains("FROM trucks")) return trucks.containsKey(key) ? List.of(trucks.get(key)) : List.of();
                if (sql.contains("FROM accounts")) return accounts.containsKey(key) ? List.of(accounts.get(key)) : List.of();
                return List.of();
            }
            return null;
        };
        JdbcTemplate jdbc = mock(JdbcTemplate.class, db);
        AccountRepository repo = mock(AccountRepository.class);
        when(repo.findById(3L)).thenReturn(Optional.of(Account.builder().accountId(3L).userId("corp").userType(Account.UserType.CORPORATE_APPROVED).companyId(1L).build()));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedAccountPrincipal(3L, "corp", 1L, "CORPORATE_APPROVED"), null, List.of()));
        api = new V9WebController(jdbc, repo, mock(HubOpsService.class), mock(PasswordEncoder.class), mock(ReturnDueService.class),
                mock(BranchService.class), new VehicleUsePolicy(jdbc));

        putTruck("서울85자6013", 1, "CONTAINER_TRUCK", "APPROVED");
        putTruck("대구80아2838", 1, "CONTAINER_TRUCK", "PENDING");
        putTruck("인천99사1235", 2, "CONTAINER_TRUCK", "APPROVED");
        accounts.put(8L, new HashMap<>(Map.of("company_id", 1L, "user_type", "CONTAINER_DRIVER", "approval_status", "APPROVED")));
        accounts.put(9L, new HashMap<>(Map.of("company_id", 1L, "user_type", "CONTAINER_DRIVER", "approval_status", "PENDING")));
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    Map<String, Object> body(String vehicle, Long driver) {
        Map<String, Object> b = new HashMap<>(Map.of("containerNo", "SQAU1102055", "sealNo", "SC-1001"));
        if (vehicle != null) b.put("vehicleNo", vehicle);
        if (driver != null) b.put("driverAccountId", driver);
        return b;
    }

    boolean dispatched() { return writes.stream().anyMatch(s -> s.contains("INSERT INTO shipment_legs") || s.contains("INSERT INTO transport_orders")); }

    @Test
    void bothEmptyIsDraftOnly() {
        assertEquals("CREATED", api.createOrder(body(null, null)).get("status"));
        assertTrue(writes.stream().noneMatch(s -> s.contains("INSERT INTO shipment_legs"))); // 운송 구간(배차) 없음
    }

    @Test
    void approvedVehicleAndValidDriverAssigned() {
        assertEquals("ASSIGNED", api.createOrder(body("서울85자6013", 8L)).get("status"));
        assertTrue(writes.stream().anyMatch(s -> s.contains("INSERT INTO shipment_legs")));
    }

    @Test
    void halfFilledOrInvalidRejectedBeforeAnyInsert() {
        assertThrows(IllegalArgumentException.class, () -> api.createOrder(body("서울85자6013", null))); // 차량만 (예전: 조용히 초안)
        assertThrows(IllegalArgumentException.class, () -> api.createOrder(body(null, 8L)));          // 기사만
        assertThrows(IllegalArgumentException.class, () -> api.createOrder(body("대구80아2838", 8L))); // 심사대기 차량
        assertThrows(IllegalArgumentException.class, () -> api.createOrder(body("서울85자6013", 9L))); // 가입 미승인 기사
        assertThrows(AccessDeniedException.class, () -> api.createOrder(body("인천99사1235", 8L)));    // 다른 운송사 차량
        assertFalse(dispatched());
    }
}
