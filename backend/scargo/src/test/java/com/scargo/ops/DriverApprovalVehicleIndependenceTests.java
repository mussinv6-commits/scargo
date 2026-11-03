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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 26.11.02 기사 가입 승인 · 반려는 차량 진입 허가(trucks.entry_approval)를 바꾸지 않는다 (차량은 진입 허가 심사에서 따로) */
class DriverApprovalVehicleIndependenceTests {
    JdbcTemplate jdbc; V9WebController api;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        AccountRepository accounts = mock(AccountRepository.class);
        Account admin = Account.builder().accountId(1L).userId("admin").userType(Account.UserType.ADMIN).build();
        when(accounts.findById(1L)).thenReturn(Optional.of(admin));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedAccountPrincipal(1L, "admin", null, "ADMIN"), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        Map<String, Object> driver = new HashMap<>(Map.of("company_id", 7L, "user_type", "BRANCH_DRIVER", "approval_status", "PENDING"));
        when(jdbc.queryForList(startsWith("SELECT company_id, user_type, approval_status FROM accounts"), (Object[]) any())).thenReturn(List.of(driver));
        when(jdbc.queryForObject(contains("v_carrier_contacts"), eq(Integer.class), (Object[]) any())).thenReturn(0);
        api = new V9WebController(jdbc, accounts, mock(HubOpsService.class), mock(PasswordEncoder.class), mock(ReturnDueService.class),
                mock(BranchService.class), mock(VehicleUsePolicy.class));
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject"})
    void decisionLeavesVehicleApprovalAlone(String action) {
        Map<String, Object> r = api.decide(42L, action, Map.of("reason", "서류 미비"));
        assertEquals("approve".equals(action) ? "APPROVED" : "REJECTED", r.get("status"));
        // jdbc.update 는 가변 인자라 인자 수와 상관없이 실제 실행된 SQL 을 모두 모아 본다
        List<String> updates = mockingDetails(jdbc).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("update")).map(i -> String.valueOf(i.getArguments()[0])).toList();
        assertTrue(updates.stream().anyMatch(s -> s.startsWith("UPDATE accounts SET approval_status")), "기사 계정은 처리됨: " + updates);
        assertTrue(updates.stream().noneMatch(s -> s.contains("trucks") || s.contains("entry_approval")), "차량 허가는 그대로: " + updates);
    }
}
