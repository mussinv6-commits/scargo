package com.scargo.controller;

import com.scargo.dto.AccountCreateRequest;
import com.scargo.dto.AccountResponse;
import com.scargo.dto.AccountUpdateRequest; // 26.10.01 병합(태수님): 회원정보 수정 DTO
import com.scargo.dto.LoginRequest;
import com.scargo.service.AccountService;
import com.scargo.entity.Account;
import com.scargo.repository.AccountRepository;
import com.scargo.security.AuthenticatedAccountPrincipal;
import java.util.Locale;
import jakarta.servlet.http.HttpServletRequest; // 추가
import jakarta.servlet.http.HttpSession; // 추가
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AccountRepository accountRepository;

    // ADMIN: 전체 유형 생성 / 운송사 담당자: 자기 업체 기사만 생성
    // 승인 대기 없이 생성 즉시 사용 가능
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')")
    public ResponseEntity<AccountResponse> createAccount(
            @RequestBody AccountCreateRequest request,
            Authentication authentication
    ) {
        Account caller = currentAccount(authentication);
        if (caller.getUserType() != Account.UserType.ADMIN) {
            requireCorporate(caller);
            String type = request.getUserType() == null
                    ? "" : request.getUserType().trim().toUpperCase(Locale.ROOT);
            if (!isDriver(type)) {
                throw new AccessDeniedException("운송사 담당자는 기사 계정만 생성할 수 있습니다.");
            }
            if (request.getCompanyId() != null
                    && !caller.getCompanyId().equals(request.getCompanyId())) {
                throw new AccessDeniedException("자기 업체의 기사만 생성할 수 있습니다.");
            }
            if (request.getHubId() != null || request.getCenterId() != null) {
                throw new IllegalArgumentException("기사 계정에는 허브·센터 소속을 지정할 수 없습니다.");
            }
            // 클라이언트 요청 대신 로그인 계정의 현재 소속 업체를 사용
            request.setCompanyId(caller.getCompanyId());
            request.setUserType(type);
        }
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
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED') or #p0 == principal.id")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable("id") Long id,
            Authentication authentication
    ) {
        Account caller = currentAccount(authentication);
        if (caller.getUserType() != Account.UserType.ADMIN
                && !caller.getAccountId().equals(id)) {
            requireCorporate(caller);
            Account target = accountRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다."));
            if (target.getUserType() == null
                    || !isDriver(target.getUserType().name())
                    || !caller.getCompanyId().equals(target.getCompanyId())) {
                throw new AccessDeniedException("자기 업체의 기사 계정만 조회할 수 있습니다.");
            }
        }
        AccountResponse response = accountService.getAccount(id);
        return ResponseEntity.ok(response);
    }

    // 26.10.01 병합(태수님 26.09.30 추가): 회원정보 수정 API (PUT /api/accounts/{id})
    // - 본인 계정 또는 관리자만 수정 가능하도록 권한 체크 추가 (다른 사람 계정 수정 방지)
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #p0 == principal.id")
    public ResponseEntity<AccountResponse> updateAccount(
            @PathVariable("id") Long id,
            @RequestBody AccountUpdateRequest request,
            Authentication authentication
    ) {
        Account caller = currentAccount(authentication);
        boolean admin = caller.getUserType() == Account.UserType.ADMIN;
        if (!admin && !caller.getAccountId().equals(id)) {
            throw new AccessDeniedException("본인 계정만 수정할 수 있습니다.");
        }

        // 소속 변경은 접근 범위를 변경하므로 관리자만 허용
        if (!admin && (request.getCompanyId() != null
                || request.getHubId() != null
                || request.getCenterId() != null
                || request.getBusinessNo() != null)) {
            throw new AccessDeniedException("소속 및 사업자번호는 관리자만 변경할 수 있습니다.");
        }

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
        httpRequest.changeSessionId();
        
        // 3. 세션에 accountId 저장 
        session.setAttribute("accountId", response.getAccountId()); 

        return ResponseEntity.ok(response);
    }

    // 26.10.01 병합: 관리자의 회원 삭제 API (DELETE /api/accounts/{id}) - 관리자 회원 관리 화면에서 사용
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAccount(@PathVariable("id") Long id) {
        accountService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }

    // 26.09.22 추가: 특정 업체 소속 기사(CONTAINER_DRIVER / LINEHAUL_DRIVER) 목록 조회 (사업자 본인 업체 또는 관리자만 가능)
    @GetMapping("/company/{companyId}/drivers")
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')")
    public ResponseEntity<List<AccountResponse>> getDriversByCompany(
            @PathVariable("companyId") Long companyId,
            Authentication authentication
    ) {
        Account caller = currentAccount(authentication);
        if (caller.getUserType() != Account.UserType.ADMIN) {
            requireCorporate(caller);
            if (!caller.getCompanyId().equals(companyId)) {
                throw new AccessDeniedException("자기 업체의 기사만 조회할 수 있습니다.");
            }
        }
        List<AccountResponse> responses = accountService.getDriversByCompany(companyId);
        return ResponseEntity.ok(responses);
    }

    // 로그인한 운송사 담당자의 현재 DB 소속 기준 기사 목록
    @GetMapping("/my-company/drivers")
    @PreAuthorize("hasRole('CORPORATE_APPROVED')")
    public ResponseEntity<List<AccountResponse>> getMyCompanyDrivers(Authentication authentication) {
        Account caller = currentAccount(authentication);
        requireCorporate(caller);
        return ResponseEntity.ok(accountService.getDriversByCompany(caller.getCompanyId()));
    }

    private Account currentAccount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedAccountPrincipal)) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
        AuthenticatedAccountPrincipal principal =
                (AuthenticatedAccountPrincipal) authentication.getPrincipal();
        return accountRepository.findById(principal.getId())
                .orElseThrow(() -> new AccessDeniedException("유효한 계정이 아닙니다."));
    }

    private void requireCorporate(Account account) {
        if (account.getUserType() != Account.UserType.CORPORATE_APPROVED
                || account.getCompanyId() == null) {
            throw new AccessDeniedException("소속 업체가 있는 운송사 담당자만 처리할 수 있습니다.");
        }
    }

    private boolean isDriver(String type) {
        return "CONTAINER_DRIVER".equals(type) || "LINEHAUL_DRIVER".equals(type);
    }
}
