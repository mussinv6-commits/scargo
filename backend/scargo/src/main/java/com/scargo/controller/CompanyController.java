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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    // 업체 등록 API (POST /api/companies)
    @PostMapping
    public ResponseEntity<CompanyResponse> createCompany(@Valid @RequestBody CompanyCreateRequest request) {
        CompanyResponse response = companyService.createCompany(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 전체 업체 목록 조회 API (GET /api/companies)
    @GetMapping
    public ResponseEntity<List<CompanyResponse>> getAllCompanies() {
        List<CompanyResponse> responses = companyService.getAllCompanies();
        return ResponseEntity.ok(responses);
    }

    // PK 기준 업체 단건 조회 API (GET /api/companies/{companyId})
    @GetMapping("/{companyId}")
    public ResponseEntity<CompanyResponse> getCompany(@PathVariable("companyId") Long companyId) {
        CompanyResponse response = companyService.getCompany(companyId);
        return ResponseEntity.ok(response);
    }

    // 사업자번호 기준 단건 조회 API (GET /api/companies/business-no/{businessNo})
    @GetMapping("/business-no/{businessNo}")
    public ResponseEntity<CompanyResponse> getCompanyByBusinessNo(@PathVariable("businessNo") String businessNo) {
        CompanyResponse response = companyService.getCompanyByBusinessNo(businessNo);
        return ResponseEntity.ok(response);
    }

    // 특정 업종별 업체 조회 API (GET /api/companies/industry?type=컨테이너 운송업)
    @GetMapping("/industry")
    public ResponseEntity<List<CompanyResponse>> getCompaniesByIndustry(@RequestParam("type") String industryType) {
        List<CompanyResponse> responses = companyService.getCompaniesByIndustryType(industryType);
        return ResponseEntity.ok(responses);
    }

    // 특정 업체명 키워드 조회 API (GET /api/companies/search?name=...)
    @GetMapping("/search")
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
    public ResponseEntity<CompanyResponse> updateCompany(
            @PathVariable("companyId") Long companyId,
            @Valid @RequestBody CompanyUpdateRequest request) {
        CompanyResponse response = companyService.updateCompany(companyId, request);
        return ResponseEntity.ok(response);
    }

    // 업체 삭제 API (DELETE /api/companies/{companyId})
    @DeleteMapping("/{companyId}")
    public ResponseEntity<Void> deleteCompany(@PathVariable("companyId") Long companyId) {
        companyService.deleteCompany(companyId);
        return ResponseEntity.noContent().build();
    }
}