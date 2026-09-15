package com.scargo.controller;

import com.scargo.dto.CompanyCreateRequest;
import com.scargo.dto.CompanyOptionResponse;
import com.scargo.dto.CompanyResponse;
import com.scargo.service.CompanyService;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<CompanyResponse> createCompany(@RequestBody CompanyCreateRequest request) {
        CompanyResponse response = companyService.createCompany(request);
        return ResponseEntity.ok(response);
    }

    // 전체 업체 목록 조회 API (GET /api/companies)
    @GetMapping
    public ResponseEntity<List<CompanyResponse>> getAllCompanies() {
        List<CompanyResponse> responses = companyService.getAllCompanies();
        return ResponseEntity.ok(responses);
    }

    // 특정 사업자번호 단건 조회 API (GET /api/companies/{businessNo})
    @GetMapping("/{businessNo}")
    public ResponseEntity<CompanyResponse> getCompany(@PathVariable("businessNo") String businessNo) {
        CompanyResponse response = companyService.getCompany(businessNo);
        return ResponseEntity.ok(response);
    }

    // 특정 업종별 업체 조회 API (GET /api/companies/industry?type=컨테이너 운송업)
    @GetMapping("/industry")
    public ResponseEntity<List<CompanyResponse>> getCompaniesByIndustry(@RequestParam("type") String industryType) {
        List<CompanyResponse> responses = companyService.getCompaniesByIndustryType(industryType);
        System.out.println("전달받은 업종: " + industryType);  // 부트상에서 입력값 확인용
        return ResponseEntity.ok(responses);
    }
    
   // 특정 업체 조회 API (GET /api/companies/search?name=...)
    // 업체명 중복이어도 타 정보(창립일, 대표명, 주소 등등)도 함께 표시
    @GetMapping("/search")
    public ResponseEntity<List<CompanyResponse>> getCompaniesByName(@RequestParam("name") String companyName) {
        List<CompanyResponse> responses = companyService.getCompaniesByCompanyName(companyName);
        System.out.println("전달받은 업체명: " + companyName); // 부트상에서 입력값 확인용
        return ResponseEntity.ok(responses);
    }
    // 업체 목록 조회
    @GetMapping("/options")
    public ResponseEntity<List<CompanyOptionResponse>> getCompanyOptions() {
        return ResponseEntity.ok(companyService.getCompanyOptions());
    }
}