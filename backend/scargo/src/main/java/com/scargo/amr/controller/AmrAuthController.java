package com.scargo.amr.controller;

import com.scargo.amr.config.AmrAuthConfig;
import com.scargo.amr.entity.AmrAuthToken;
import com.scargo.amr.service.AmrAuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

/**
 * 26.10.16 AMR 관리자 앱 인증 API (이 경로만 토큰 없이 호출 가능)
 *  GET  /api/amr/auth/status   내 토큰 유효 여부 · 팀 ADMIN 로그인 여부
 *  GET  /api/amr/auth/code     앱 연결 코드 6자리 + 노트북 주소 (웹 · 팀 ADMIN 로그인 필요)
 *  POST /api/amr/auth/rotate   모든 앱 로그아웃 (웹 · 팀 ADMIN 로그인 필요)
 *  POST /api/amr/auth/login    6자리 → 24시간 토큰 (앱)
 *  POST /api/amr/auth/logout   토큰 폐기
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
        m.put("valid", t != null);
        m.put("expiresAt", t == null ? null : t.getExpiresAt());
        m.put("admin", AmrAuthConfig.adminId() != null);
        return m;
    }

    @GetMapping("/code")
    public Map<String, Object> code(HttpServletRequest req) {
        return auth.currentCode(requireAdmin(), req.getLocalPort());
    }

    @PostMapping("/rotate")
    public Map<String, Object> rotate() {
        auth.rotate(requireAdmin());
        return Map.of("ok", true);
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

    private static String requireAdmin() {
        String id = AmrAuthConfig.adminId();
        if (id == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "웹에서 관리자(ADMIN)로 로그인해야 앱 연결 코드를 볼 수 있습니다");
        return id;
    }
}
