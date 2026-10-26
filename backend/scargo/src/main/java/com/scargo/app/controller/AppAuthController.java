package com.scargo.app.controller;

import com.scargo.app.dto.AppDtos.LoginRequest;
import com.scargo.app.dto.AppDtos.LoginResponse;
import com.scargo.app.dto.AppDtos.MeResponse;
import com.scargo.app.security.CurrentAccount;
import com.scargo.app.service.AppAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 26.10.03 2차: 기사 앱 인증
 *  POST /api/app/auth/login   (공개)   → { token, expiresAt, me }
 *  POST /api/app/auth/logout
 *  GET  /api/app/me
 *  GET  /api/app/health       (공개)   → 앱의 "서버 연결 확인" 버튼용
 */
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppAuthController {

    private final AppAuthService appAuthService;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("ok", true, "service", "scargo-app");
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return appAuthService.login(request);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        appAuthService.logout(authorization);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeResponse me() {
        return appAuthService.me(CurrentAccount.id());
    }
}
