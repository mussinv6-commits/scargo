package com.scargo.service;

import com.scargo.dto.CompanyCreateRequest;
import com.scargo.dto.CompanyOptionResponse;
import com.scargo.dto.CompanyResponse;
import com.scargo.entity.Company;
import com.scargo.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyService {

    private final CompanyRepository companyRepository;

    // 업체 등록 로직
    @Transactional
    public CompanyResponse createCompany(CompanyCreateRequest request) {
        // 사업자 등록번호 중복 체크
        companyRepository.findByBusinessNo(request.getBusinessNo())
                .ifPresent(c -> {
                    throw new IllegalArgumentException("이미 등록된 사업자번호입니다.");
                });

        Company company = Company.builder()
                .businessNo(request.getBusinessNo())
                .companyName(request.getCompanyName())
                .industryType(request.getIndustryType())
                .representativeName(request.getRepresentativeName())
                .address(request.getAddress())
                .build();

        Company savedCompany = companyRepository.save(company);
        return new CompanyResponse(savedCompany);
    }

    // 전체 업체 목록 조회
    public List<CompanyResponse> getAllCompanies() {
        return companyRepository.findAll().stream()
                .map(CompanyResponse::new)
                .collect(Collectors.toList());
    }

    // 단건 업체 조회 (사업자번호 기준)
    public CompanyResponse getCompany(String businessNo) {
        Company company = companyRepository.findByBusinessNo(businessNo)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 업체입니다. 사업자번호: " + businessNo));
        return new CompanyResponse(company);
    }
    
   // 특정 업종별 업체 조회
    public List<CompanyResponse> getCompaniesByIndustryType(String industryType) {
        return companyRepository.findByIndustryTypeContaining(industryType).stream()
                .map(CompanyResponse::new)
                .collect(Collectors.toList());
    }
    
    // 업체명 조회(회원 가입시 필요)
    public List<CompanyResponse> getCompaniesByCompanyName(String companyName) {
        return companyRepository.findByCompanyNameContaining(companyName).stream()
                .map(CompanyResponse::new)
                .collect(Collectors.toList());
    }
    // 업체 목록 조회
    public List<CompanyOptionResponse> getCompanyOptions() {
        return companyRepository.findAll().stream()
                .map(CompanyOptionResponse::new)
                .collect(Collectors.toList());
    }
}