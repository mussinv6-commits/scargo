package com.scargo.amr.controller;

import com.scargo.amr.entity.AmrAuthToken;
import com.scargo.amr.security.AmrTokenAuthenticationFilter;
import com.scargo.amr.service.AmrAuthService;
import com.scargo.security.AuthenticatedAccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * 26.10.16 AMR 관리자 앱 인증 API (이 경로만 토큰 없이 호출 가능)
 *  GET  /api/amr/auth/status   내 토큰 유효 여부 · 웹 로그인 계정이 코드를 다룰 수 있는지
 *  GET  /api/amr/auth/code     앱 연결 코드 6자리 + 노트북 주소 (웹 · ADMIN 또는 HUB_OP 로그인 필요)
 *  POST /api/amr/auth/rotate   모든 앱 로그아웃 (웹 · ADMIN = 전체, HUB_OP = 소속 허브)
 *  POST /api/amr/auth/login    6자리 → 24시간 토큰 (앱)
 *  POST /api/amr/auth/logout   토큰 폐기
 * 26.10.22 코드 발급을 ADMIN 전용 → ADMIN · HUB_OP 로 넓히고, HUB_OP 코드로 받은 출입증은 소속 허브만 쓰게 함
 */
@RestController
@RequestMapping("/api/amr/auth")
@RequiredArgsConstructor
public class AmrAuthController {

    private final AmrAuthService auth;

    public record CodeRequest(String code, String client) {}

    @GetMapping("/status")
    public Map<String, Object> status(HttpServletRequest req) {
        AmrAuthToken t = auth.check(req.getHeader(AmrTokenAuthenticationFilter.HEADER));
        AuthenticatedAccountPrincipal web = webAccount();
        boolean issuer = web != null && ("ADMIN".equals(web.getUserType())
                || ("HUB_OP".equals(web.getUserType()) && web.getHubId() != null));
        Map<String, Object> m = new HashMap<>();
        m.put("valid", t != null);
        m.put("expiresAt", t == null ? null : t.getExpiresAt());
        m.put("admin", issuer); // 기존 화면 호환: true면 앱 연결 코드를 보여 줄 수 있음
        m.put("role", web == null ? null : web.getUserType());
        m.put("hubId", web == null ? null : web.getHubId());
        return m;
    }

    @GetMapping("/code")
    @PreAuthorize("hasAnyRole('ADMIN', 'HUB_OP')")
    public Map<String, Object> code(HttpServletRequest req) {
        return auth.currentCode(req.getLocalPort());
    }

    @PostMapping("/rotate")
    @PreAuthorize("hasAnyRole('ADMIN', 'HUB_OP')")
    public Map<String, Object> rotate() {
        auth.rotate();
        return Map.of("ok", true);
    }

    @PostMapping("/login")
    public AmrAuthService.LoginResult login(@RequestBody CodeRequest body, HttpServletRequest req) {
        return auth.login(body == null ? null : body.code(), body == null ? null : body.client(), req.getRemoteAddr(), req.getHeader("User-Agent"));
    }

    // 26.10.23 출입증 감사 조회: ADMIN = 전체 출입증 + 실패 기록, HUB_OP = 소속 허브 키로 받은 출입증 (토큰 · 코드 값은 내려주지 않음)
    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('ADMIN', 'HUB_OP')")
    public Map<String, Object> audit() {
        return auth.audit();
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest req) {
        auth.logout(req.getHeader(AmrTokenAuthenticationFilter.HEADER));
        return Map.of("ok", true);
    }

    // 실패 이유를 화면에 그대로 보여 주려고 메시지를 JSON으로 돌려준다 (이 컨트롤러에만 적용)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> fail(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "인증 실패" : e.getReason()));
    }

    // 코드 · 로그아웃 권한 없음: 기존처럼 403 + 안내 메시지
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> denied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message",
                "웹에서 관리자(ADMIN) 또는 허브 관제사(HUB_OP)로 로그인해야 앱 연결 코드를 볼 수 있습니다"));
    }

    private static AuthenticatedAccountPrincipal webAccount() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof AuthenticatedAccountPrincipal p ? p : null;
    }
}
