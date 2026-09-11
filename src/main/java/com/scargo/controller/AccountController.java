package com.scargo.controller;

import com.scargo.dto.AccountCreateRequest;
import com.scargo.dto.AccountResponse;
import com.scargo.service.AccountService;
import com.scargo.dto.LoginRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

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

    // 전체 계정 조회 API (GET /api/accounts)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")  // 관리자만 전체 회원 조회 가능 , postman이나 프론트에서 직접확인하고 싶을시 주석처리
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
    public ResponseEntity<AccountResponse> login(@RequestBody LoginRequest request) {
        AccountResponse response = accountService.login(request);
        return ResponseEntity.ok(response);
    }
}