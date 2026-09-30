package com.scargo.controller;

import com.scargo.dto.AccountCreateRequest;
import com.scargo.dto.AccountResponse;
import com.scargo.dto.LoginRequest;
import com.scargo.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    // 계정 생성 API (POST /api/accounts)
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@RequestBody AccountCreateRequest request) {
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.ok(response);
    }

    // 아이디 중복 확인 API (GET /api/accounts/check-id/{userId})
    @GetMapping("/check-id/{userId}")
    public ResponseEntity<String> checkUserId(@PathVariable("userId") String userId) {
        boolean available = accountService.isUserIdAvailable(userId);
        return ResponseEntity.ok(available ? "YES" : "NO");
    }

    // 전체 계정 조회 API (GET /api/accounts)
    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        List<AccountResponse> responses = accountService.getAllAccounts();
        return ResponseEntity.ok(responses);
    }

    // 특정 계정 조회 API (GET /api/accounts/{id})
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable("id") Long id) {
        AccountResponse response = accountService.getAccount(id);
        return ResponseEntity.ok(response);
    }

    // 로그인 API (POST /api/accounts/login)
    @PostMapping("/login")
    public ResponseEntity<AccountResponse> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        // 서비스 단에서 SecurityContext 및 세션 생성을 함께 진행합니다.
        AccountResponse response = accountService.login(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    // 관리자의 기업계정 승인 API (PATCH /api/accounts/{id}/approve)
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> approveCorporateAccount(@PathVariable("id") Long id) {
        accountService.approveCorporateAccount(id);
        return ResponseEntity.ok().build();
    }
}