package com.scargo.repository;

import com.scargo.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;  

//Optional : 널포인트예외 방지 및 예외처리용

//Repository 안해도 상관없으나 하면 예외처리에 용이
@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    
    // 명시적으로 LIKE '%keyword%' 쿼리 작성 
	// 왜인지는 모르겠으나 해당 코드 없으면 조회기능이 작동 안됨..
	// 업종별 조회
    @Query("SELECT c FROM Company c WHERE c.industryType LIKE CONCAT('%', :industryType, '%')")
    List<Company> findByIndustryTypeContaining(@Param("industryType") String industryType);
    
    // 업체이름 조회
    @Query("SELECT c FROM Company c WHERE c.companyName LIKE CONCAT('%', :companyName, '%')")
    List<Company> findByCompanyNameContaining(@Param("companyName") String companyName);
    
   // 회원가입 시 업체명과 주소로 정확히 일치하는 업체를 찾기 위한 메서드 추가
    Optional<Company> findByCompanyNameAndAddress(String companyName, String address);
    
   // 사업자 번호로 업체 찾기
    Optional<Company> findByBusinessNo(String businessNo);
}