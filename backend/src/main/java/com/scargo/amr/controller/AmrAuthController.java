package com.scargo.amr.controller;

import com.scargo.amr.config.AmrAuthConfig;
import com.scargo.amr.entity.AmrAuthToken;
import com.scargo.amr.service.AmrAuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * 26.10.16 AMR 관리자 OTP 인증 API (이 경로만 토큰 없이 호출 가능)
 *  GET  /api/amr/auth/status         등록 여부 · 내 토큰 유효 여부
 *  POST /api/amr/auth/setup          QR 만들기   (웹 · 팀 ADMIN 로그인 필요)
 *  POST /api/amr/auth/setup/confirm  6자리 확인 후 등록 + 바로 로그인 (웹 · 팀 ADMIN 로그인 필요)
 *  POST /api/amr/auth/login          6자리 → 24시간 토큰 (웹 · 앱)
 *  POST /api/amr/auth/logout         토큰 폐기
 */
@RestController
@RequestMapping("/api/amr/auth")
@RequiredArgsConstructor
public class AmrAuthController {

    private final AmrAuthService auth;

    public record CodeRequest(String code, String client) {}

    @GetMapping("/status")
    public Map<String, Object> status(HttpServletRequest req) {
        AmrAuthToken t = auth.check(req.getHeader(AmrAuthConfig.HEADER));
        Map<String, Object> m = new HashMap<>();
        m.put("registered", auth.registered());
        m.put("valid", t != null);
        m.put("expiresAt", t == null ? null : t.getExpiresAt());
        m.put("admin", adminId() != null);
        return m;
    }

    @PostMapping("/setup")
    public Map<String, Object> setup() {
        return auth.startSetup(requireAdmin());
    }

    @PostMapping("/setup/confirm")
    public AmrAuthService.LoginResult confirm(@RequestBody CodeRequest body, HttpServletRequest req) {
        requireAdmin();
        return auth.confirmSetup(body == null ? null : body.code(), req.getRemoteAddr());
    }

    @PostMapping("/login")
    public AmrAuthService.LoginResult login(@RequestBody CodeRequest body, HttpServletRequest req) {
        return auth.login(body == null ? null : body.code(), body == null ? null : body.client(), req.getRemoteAddr());
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest req) {
        auth.logout(req.getHeader(AmrAuthConfig.HEADER));
        return Map.of("ok", true);
    }

    // 실패 이유를 화면에 그대로 보여 주려고 메시지를 JSON으로 돌려준다 (이 컨트롤러에만 적용)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> fail(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "인증 실패" : e.getReason()));
    }

    /** 팀 공통 로그인(세션)으로 들어온 ADMIN 이면 아이디, 아니면 null */
    private static String adminId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) return null;
        boolean admin = a.getAuthorities().stream().anyMatch((g) -> "ROLE_ADMIN".equals(g.getAuthority()));
        return admin ? a.getName() : null;
    }

    private static String requireAdmin() {
        String id = adminId();
        if (id == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "OTP 등록은 웹에서 관리자(ADMIN)로 로그인한 상태에서만 할 수 있습니다");
        return id;
    }
}
