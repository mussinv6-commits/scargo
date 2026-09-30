package com.scargo.repository;

import com.scargo.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    // 업종별 키워드 조회
    @Query("SELECT c FROM Company c WHERE c.industryType LIKE CONCAT('%', :industryType, '%')")
    List<Company> findByIndustryTypeContaining(@Param("industryType") String industryType);

    // 업체명 키워드 조회
    @Query("SELECT c FROM Company c WHERE c.companyName LIKE CONCAT('%', :companyName, '%')")
    List<Company> findByCompanyNameContaining(@Param("companyName") String companyName);

    // 업체명과 주소로 정확히 일치하는 업체 조회 (회원가입 등)
    Optional<Company> findByCompanyNameAndAddress(String companyName, String address);

    // 업체명과 주소 존재 여부 확인 (복합 유니크 제약조건 uk_company_name_address 검증용)
    boolean existsByCompanyNameAndAddress(String companyName, String address);

    // 사업자 번호로 업체 찾기
    Optional<Company> findByBusinessNo(String businessNo);
}