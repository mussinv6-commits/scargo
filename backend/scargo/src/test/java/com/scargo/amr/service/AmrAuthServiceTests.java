package com.scargo.amr.service;

import com.scargo.amr.entity.AmrAuthToken;
import com.scargo.amr.entity.AmrOtpKey;
import com.scargo.amr.repository.AmrAuthTokenRepository;
import com.scargo.amr.repository.AmrOtpKeyRepository;
import com.scargo.amr.security.AmrAccessPolicy;
import com.scargo.security.AuthenticatedAccountPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 26.10.21 AMR 출입증: 사람에 묶기 · 폐기 즉시 거부 · 전체 실패 한도
 * 26.10.26 통합본: share 의 허브별 키 방식(AmrAccessPolicy) 기준으로 다시 작성
 *  - 출입증 발급자 = 코드를 마지막으로 화면에 띄운 웹 계정 (issued_by_*)
 *  - 실패 감사 = amr_auth_failures INSERT (JdbcTemplate)
 */
class AmrAuthServiceTests {
    AmrAuthTokenRepository tokens; AmrOtpKeyRepository keys; AmrAccessPolicy access; JdbcTemplate jdbc; AmrAuthService svc;
    final Map<String, AmrAuthToken> store = new HashMap<>();
    AmrOtpKey key;

    @BeforeEach
    void setUp() {
        tokens = mock(AmrAuthTokenRepository.class); keys = mock(AmrOtpKeyRepository.class);
        access = mock(AmrAccessPolicy.class); jdbc = mock(JdbcTemplate.class);
        key = new AmrOtpKey(); key.setKeyId(1L); key.setSecret("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"); key.setActive(true);
        when(keys.findFirstByActiveTrueAndHubIdIsNullOrderByKeyIdDesc()).thenReturn(Optional.of(key));
        when(keys.findByActiveTrueOrderByKeyIdDesc()).thenReturn(List.of(key));
        when(keys.findById(1L)).thenReturn(Optional.of(key));
        when(keys.findAll()).thenReturn(List.of(key));
        when(access.issuerHubId()).thenReturn(null);   // ADMIN = 전체 허브
        when(access.actorName()).thenReturn("admin");
        when(access.defaultHubId()).thenReturn(1L);
        when(tokens.save(any())).thenAnswer(i -> { AmrAuthToken t = i.getArgument(0); store.put(t.getTokenHash(), t); return t; });
        when(tokens.findById(any())).thenAnswer(i -> Optional.ofNullable(store.get((String) i.getArgument(0))));
        svc = new AmrAuthService(keys, tokens, mock(AmrService.class), access, jdbc);
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    /** 웹에 로그인한 관리자가 코드를 화면에 띄운다 */
    private String codeShownBy(long accountId, String user) {
        AuthenticatedAccountPrincipal p = new AuthenticatedAccountPrincipal(accountId, user, null, "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        String code = (String) svc.currentCode(8080).get("code");
        SecurityContextHolder.clearContext(); // 앱은 웹 로그인 없이 코드만 보낸다
        return code;
    }

    @Test
    void tokenIsTiedToAdminWhoShowedTheCode() {
        String code = codeShownBy(2L, "admin");
        AmrAuthService.LoginResult r = svc.login(code, "APP", "10.0.0.5", "JUnit");
        AmrAuthToken t = store.values().iterator().next();
        assertEquals(2L, t.getIssuedByAccountId());
        assertEquals("admin", t.getIssuedByUserId());
        assertEquals("JUnit", t.getUserAgent());
        assertNotNull(svc.check(r.token()));
    }

    @Test
    void sameCodeCannotBeReused() {
        String code = codeShownBy(2L, "admin");
        svc.login(code, "APP", "10.0.0.6", null);
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> svc.login(code, "APP", "10.0.0.6", null));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
    }

    @Test
    void revokedAndExpiredTokensAreRejectedImmediately() {
        AmrAuthService.LoginResult r = svc.login(codeShownBy(2L, "admin"), "APP", "10.0.0.7", null);
        AmrAuthToken t = store.values().iterator().next();
        when(tokens.findByTokenHashStartingWith(t.getTokenHash().substring(0, 12))).thenReturn(List.of(t));
        svc.revokeOne(t.getTokenHash().substring(0, 12));
        assertNull(svc.check(r.token()));
        assertEquals("ADMIN_REVOKE", t.getRevokeReason());
        assertEquals("admin", t.getRevokedBy());

        svc.resetGlobalForTest();
        store.clear();
        // 다음 30초 구간 코드가 필요하므로 재사용 방지 기록을 피하려고 새 키로 바꾼다
        AmrOtpKey k2 = new AmrOtpKey(); k2.setKeyId(2L); k2.setSecret("KRSXG5CTMVRXEZLUKRSXG5CTMVRXEZLU"); k2.setActive(true);
        when(keys.findFirstByActiveTrueAndHubIdIsNullOrderByKeyIdDesc()).thenReturn(Optional.of(k2));
        when(keys.findByActiveTrueOrderByKeyIdDesc()).thenReturn(List.of(k2));
        when(keys.findById(2L)).thenReturn(Optional.of(k2));
        AmrAuthService.LoginResult r2 = svc.login(codeShownBy(3L, "sbot"), "APP", "10.0.0.8", null);
        store.values().forEach(x -> x.setExpiresAt(OffsetDateTime.now().minusSeconds(1)));
        assertNull(svc.check(r2.token()));
    }

    @Test
    void logoutRevokes() {
        AmrAuthService.LoginResult r = svc.login(codeShownBy(2L, "admin"), "APP", "10.0.0.9", null);
        svc.logout(r.token());
        assertNull(svc.check(r.token()));
        assertEquals("LOGOUT", store.values().iterator().next().getRevokeReason());
    }

    @Test
    void globalFailLimitAcrossManyIps() {
        codeShownBy(2L, "admin");
        for (int i = 0; i < AmrAuthService.GLOBAL_MAX_FAIL; i++) {
            final String ip = "192.168.0." + i; // IP 마다 1번씩 → IP 잠금은 안 걸림
            assertThrows(ResponseStatusException.class, () -> svc.login("999999", "APP", ip, null));
        }
        String good = codeShownBy(2L, "admin");
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> svc.login(good, "APP", "172.16.0.1", null));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatusCode());
        // 실패마다 감사 기록 (BAD_CODE × 20 + GLOBAL_LOCKED × 1)
        verify(jdbc, atLeast(AmrAuthService.GLOBAL_MAX_FAIL + 1))
                .update(startsWith("INSERT INTO amr_auth_failures"), any(), any(), any());
    }
}
