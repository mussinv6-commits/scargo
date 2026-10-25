package com.scargo.repository;

import com.scargo.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List; // [추가] List 임포트
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
	Optional<Account> findByUserId(String userId);
	boolean existsByUserId(String userId);
	
	// [추가] 특정 회원 타입(예: ADMIN)을 가진 모든 계정 조회
	List<Account> findByUserType(Account.UserType userType);

	// 26.09.22 추가: 특정 업체 소속의 특정 회원 타입(예: 업체 소속 기사) 계정 조회 - 사업자용 기사 배정 화면에 사용
	List<Account> findByCompanyIdAndUserType(Long companyId, Account.UserType userType);
}