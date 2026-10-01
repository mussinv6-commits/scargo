package com.scargo.controller;

import com.scargo.dto.AccountCreateRequest;
import com.scargo.dto.AccountResponse;
import com.scargo.dto.AccountUpdateRequest; // 26.10.01 병합(태수님): 회원정보 수정 DTO
import com.scargo.dto.LoginRequest;
import com.scargo.service.AccountService;
import jakarta.servlet.http.HttpServletRequest; // 추가
import jakarta.servlet.http.HttpSession; // 추가
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
    @PreAuthorize("hasRole('ADMIN')")
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

    // 26.10.01 병합(태수님 26.09.30 추가): 회원정보 수정 API (PUT /api/accounts/{id})
    // - 본인 계정 또는 관리자만 수정 가능하도록 권한 체크 추가 (다른 사람 계정 수정 방지)
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == principal.id")
    public ResponseEntity<AccountResponse> updateAccount(
            @PathVariable("id") Long id,
            @RequestBody AccountUpdateRequest request
    ) {
        AccountResponse response = accountService.updateAccount(id, request);
        return ResponseEntity.ok(response);
    }

    // 로그인 API (POST /api/accounts/login) - 세션 저장 로직 추가
    @PostMapping("/login")
    public ResponseEntity<AccountResponse> login(
            @RequestBody LoginRequest request,
            HttpServletRequest httpRequest // 1. HttpServletRequest 파라미터 추가
    ) {
        AccountResponse response = accountService.login(request);

        // 2. 세션 생성 및 저장 (기존 세션이 없으면 신규 생성)
        HttpSession session = httpRequest.getSession(true);
        
        // 3. 세션에 accountId 저장 
        session.setAttribute("accountId", response.getAccountId()); 

        return ResponseEntity.ok(response);
    }

    // 관리자의 기업계정 승인 API (PATCH /api/accounts/{id}/approve)
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> approveCorporateAccount(@PathVariable("id") Long id) {
        accountService.approveCorporateAccount(id);
        return ResponseEntity.ok().build();
    }

    // 26.09.22 추가: 관리자의 기업계정 거절 API (PATCH /api/accounts/{id}/reject)
    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> rejectCorporateAccount(@PathVariable("id") Long id) {
        accountService.rejectCorporateAccount(id);
        return ResponseEntity.ok().build();
    }

    // 26.09.22 추가: 특정 업체 소속 기사(GENERAL) 목록 조회 (사업자 본인 업체 또는 관리자만 가능)
    @GetMapping("/company/{companyId}/drivers")
    @PreAuthorize("hasRole('ADMIN') or #companyId == principal.companyId")
    public ResponseEntity<List<AccountResponse>> getDriversByCompany(@PathVariable("companyId") Long companyId) {
        List<AccountResponse> responses = accountService.getDriversByCompany(companyId);
        return ResponseEntity.ok(responses);
    }

    // 26.09.22 추가: 로그인한 사업자 본인 업체의 기사 목록 조회 (companyId를 프론트가 직접 넘기지 않아도 됨)
    // - 프론트에 저장된 companyId가 최신이 아닐 경우에도 항상 정확한 세션 기준으로 조회됨
    @GetMapping("/my-company/drivers")
    @PreAuthorize("hasRole('CORPORATE_APPROVED')")
    public ResponseEntity<List<AccountResponse>> getMyCompanyDrivers(
            org.springframework.security.core.Authentication authentication) {
        com.scargo.security.AuthenticatedAccountPrincipal principal =
                (com.scargo.security.AuthenticatedAccountPrincipal) authentication.getPrincipal();
        List<AccountResponse> responses = accountService.getDriversByCompany(principal.getCompanyId());
        return ResponseEntity.ok(responses);
    }
}