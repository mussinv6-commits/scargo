package com.scargo.security;

import com.scargo.entity.Account;
import com.scargo.ops.CarrierLegacy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 26.10.21 역할 분리: S-Bot 관리자는 ROLE_ADMIN 이 없고, AMR 외 /api/** 는 차단 */
class SbotScopeTests {

    @Test
    void sbotAdminGetsOnlyAmrRole() {
        Account sbot = Account.builder().userId("sbot").userType(Account.UserType.ADMIN).build();
        assertEquals(java.util.List.of("ROLE_AMR_ADMIN"), CarrierLegacy.roles(sbot));
        Account admin = Account.builder().userId("admin").userType(Account.UserType.ADMIN).build();
        assertTrue(CarrierLegacy.roles(admin).contains("ROLE_ADMIN"));
        assertFalse(CarrierLegacy.roles(admin).contains("ROLE_AMR_ADMIN"));
    }

    @Test
    void allowListForSbot() {
        assertTrue(SbotScopeFilter.allowed("GET", "/api/amr/state"));
        assertTrue(SbotScopeFilter.allowed("POST", "/api/accounts/login"));
        assertTrue(SbotScopeFilter.allowed("POST", "/api/accounts/logout"));
        assertFalse(SbotScopeFilter.allowed("GET", "/api/accounts"));
        assertFalse(SbotScopeFilter.allowed("GET", "/api/admin/anything"));
        assertFalse(SbotScopeFilter.allowed("GET", "/api/web/exceptions"));
        assertFalse(SbotScopeFilter.allowed("POST", "/api/v1/posts"));
        assertFalse(SbotScopeFilter.allowed("GET", "/api/amrx"));
    }
}
