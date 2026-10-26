package com.scargo.controller;

import com.scargo.dto.CompanyCreateRequest;
import com.scargo.dto.CompanyOptionResponse;
import com.scargo.dto.CompanyResponse;
import com.scargo.dto.CompanyUpdateRequest;
import com.scargo.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;
    private final com.scargo.config.SignupRateLimiter signupLimiter; // fix_1330 (scargo_1)

    // 업체 등록 API (POST /api/companies)
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')") // 26.10.22 수정: 업체 등록·수정·삭제·전체 조회는 관리자만 (기존에는 비로그인도 가능했음)
    public ResponseEntity<CompanyResponse> createCompany(@Valid @RequestBody CompanyCreateRequest request) {
        CompanyResponse response = companyService.createCompany(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // fix_1300 (scargo_1): 회원가입 화면에서 목록에 없는 업체를 새로 등록 (비로그인 허용, 등록만 가능)
    // fix_1330: 승인 대기(PENDING)로 생성, IP 당 시간당 5건 제한 (초과 429)
    @PostMapping("/signup")
    public ResponseEntity<?> createCompanyForSignup(@Valid @RequestBody CompanyCreateRequest request, jakarta.servlet.http.HttpServletRequest http) {
        if (!signupLimiter.tryAcquire(http.getRemoteAddr()))
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(java.util.Map.of("message", "업체 등록 요청이 너무 많습니다. 1시간 뒤 다시 시도하세요."));
        return ResponseEntity.status(HttpStatus.CREATED).body(companyService.createForSignup(request));
    }

    // fix_1330 (scargo_1): 관리자 업체 승인·반려 (업체 관리 화면 '승인 대기 업체')
    @PatchMapping("/{companyId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public CompanyResponse approve(@PathVariable("companyId") Long companyId) {
        return companyService.approve(companyId, com.scargo.app.security.CurrentAccount.id());
    }

    @PatchMapping("/{companyId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public CompanyResponse reject(@PathVariable("companyId") Long companyId, @RequestBody(required = false) java.util.Map<String, String> b) {
        return companyService.reject(companyId, b == null ? null : b.get("reason"));
    }

    // 전체 업체 목록 조회 API (GET /api/companies)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CompanyResponse>> getAllCompanies() {
        List<CompanyResponse> responses = companyService.getAllCompanies();
        return ResponseEntity.ok(responses);
    }

    // PK 기준 업체 단건 조회 API (GET /api/companies/{companyId})
    // 관리자 또는 자기 소속 업체만 조회
    @GetMapping("/{companyId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('COMPANY_' + #companyId)")
    public ResponseEntity<CompanyResponse> getCompany(@P("companyId") @PathVariable("companyId") Long companyId) {
        CompanyResponse response = companyService.getCompany(companyId);
        return ResponseEntity.ok(response);
    }

    // 사업자번호 기준 단건 조회 API (GET /api/companies/business-no/{businessNo})
    @GetMapping("/business-no/{businessNo}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CompanyResponse> getCompanyByBusinessNo(@PathVariable("businessNo") String businessNo) {
        CompanyResponse response = companyService.getCompanyByBusinessNo(businessNo);
        return ResponseEntity.ok(response);
    }

    // 특정 업종별 업체 조회 API (GET /api/companies/industry?type=컨테이너 운송업)
    @GetMapping("/industry")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CompanyResponse>> getCompaniesByIndustry(@RequestParam("type") String industryType) {
        List<CompanyResponse> responses = companyService.getCompaniesByIndustryType(industryType);
        return ResponseEntity.ok(responses);
    }

    // 특정 업체명 키워드 조회 API (GET /api/companies/search?name=...)
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CompanyResponse>> getCompaniesByName(@RequestParam("name") String companyName) {
        List<CompanyResponse> responses = companyService.getCompaniesByCompanyName(companyName);
        return ResponseEntity.ok(responses);
    }

    // 드롭다운/선택용 업체 목록 조회 API (GET /api/companies/options)
    @GetMapping("/options")
    public ResponseEntity<List<CompanyOptionResponse>> getCompanyOptions() {
        return ResponseEntity.ok(companyService.getCompanyOptions());
    }

    // 업체 정보 수정 API (PUT /api/companies/{companyId})
    @PutMapping("/{companyId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CompanyResponse> updateCompany(
            @PathVariable("companyId") Long companyId,
            @Valid @RequestBody CompanyUpdateRequest request) {
        CompanyResponse response = companyService.updateCompany(companyId, request);
        return ResponseEntity.ok(response);
    }

    // 업체 삭제 API (DELETE /api/companies/{companyId})
    @DeleteMapping("/{companyId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCompany(@PathVariable("companyId") Long companyId) {
        companyService.deleteCompany(companyId);
        return ResponseEntity.noContent().build();
    }
}