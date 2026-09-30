package com.scargo.service;

import com.scargo.dto.CompanyCreateRequest;
import com.scargo.dto.CompanyOptionResponse;
import com.scargo.dto.CompanyResponse;
import com.scargo.dto.CompanyUpdateRequest;
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
        // 1. 업체명 + 주소 복합 중복 체크 (uk_company_name_address)
        if (companyRepository.existsByCompanyNameAndAddress(request.getCompanyName(), request.getAddress())) {
            throw new IllegalArgumentException("이미 동일한 업체명과 주소로 등록된 업체가 존재합니다.");
        }

        // 2. 사업자 등록번호 중복 체크 (입력된 경우에만 검증)
        if (request.getBusinessNo() != null && !request.getBusinessNo().isBlank()) {
            companyRepository.findByBusinessNo(request.getBusinessNo())
                    .ifPresent(c -> {
                        throw new IllegalArgumentException("이미 등록된 사업자번호입니다.");
                    });
        }

        Company company = Company.builder()
                .companyName(request.getCompanyName())
                .address(request.getAddress())
                .businessNo(request.getBusinessNo())
                .industryType(request.getIndustryType())
                .representativeName(request.getRepresentativeName())
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

    // 단건 업체 조회 (PK: companyId 기준)
    public CompanyResponse getCompany(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 업체입니다. ID: " + companyId));
        return new CompanyResponse(company);
    }

    // 단건 업체 조회 (사업자번호 기준)
    public CompanyResponse getCompanyByBusinessNo(String businessNo) {
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

    // 업체명 조회(회원 가입 시 필요)
    public List<CompanyResponse> getCompaniesByCompanyName(String companyName) {
        return companyRepository.findByCompanyNameContaining(companyName).stream()
                .map(CompanyResponse::new)
                .collect(Collectors.toList());
    }

    // 드롭다운/옵션용 업체 목록 조회
    public List<CompanyOptionResponse> getCompanyOptions() {
        return companyRepository.findAll().stream()
                .map(CompanyOptionResponse::new)
                .collect(Collectors.toList());
    }

    // 업체 정보 수정 로직
    @Transactional
    public CompanyResponse updateCompany(Long companyId, CompanyUpdateRequest request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 업체입니다. ID: " + companyId));

        // 업체명 또는 주소 변경 시 복합 중복 검증
        String newName = request.getCompanyName() != null ? request.getCompanyName() : company.getCompanyName();
        String newAddress = request.getAddress() != null ? request.getAddress() : company.getAddress();

        if ((!newName.equals(company.getCompanyName()) || !newAddress.equals(company.getAddress()))
                && companyRepository.existsByCompanyNameAndAddress(newName, newAddress)) {
            throw new IllegalArgumentException("이미 동일한 업체명과 주소로 등록된 업체가 존재합니다.");
        }

        // 사업자번호 변경 시 중복 검증
        if (request.getBusinessNo() != null && !request.getBusinessNo().equals(company.getBusinessNo())) {
            companyRepository.findByBusinessNo(request.getBusinessNo())
                    .ifPresent(c -> {
                        throw new IllegalArgumentException("이미 등록된 사업자번호입니다.");
                    });
        }

        // 엔티티 내부 update 비즈니스 메서드를 통해 변경 사항 반영
        company.update(
                request.getCompanyName(),
                request.getAddress(),
                request.getBusinessNo(),
                request.getIndustryType(),
                request.getRepresentativeName()
        );

        return new CompanyResponse(company);
    }

    // 업체 삭제 로직
    @Transactional
    public void deleteCompany(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 업체입니다. ID: " + companyId));

        companyRepository.delete(company);
    }
}