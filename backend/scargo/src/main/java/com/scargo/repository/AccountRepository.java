package com.scargo.repository;

import com.scargo.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    // 로그인 아이디로 계정 조회
    // 계정이 없을 수 있으므로 Optional로 반환
    Optional<Account> findByUserId(String userId);

    // 로그인 아이디 중복 검사
    // 이미 존재하면 true, 없으면 false
    boolean existsByUserId(String userId);

    // 특정 권한의 계정 목록 조회
    // 예: ADMIN 계정 목록 조회
    List<Account> findByUserType(Account.UserType userType);

    // 특정 업체에 소속된 특정 권한의 계정 목록 조회
    // 예: 업체 1에 소속된 BRANCH_DRIVER 목록
    List<Account> findByCompanyIdAndUserType(
            Long companyId,
            Account.UserType userType
    );

    /*
     * 지정한 운영 계열사에 직접 또는 간접 연결된 계정 목록 조회
     *
     * 연결 경로 4가지:
     * 1. 계정 → 운영 계열사
     * 2. 계정 → 소속 업체 → 운영 계열사
     * 3. 계정 → 소속 허브 → 허브 운영 업체 → 운영 계열사
     * 4. 계정 → 소속 지역센터 → 운영 계열사
     *
     * OR 조건이므로 위 경로 중 하나만 일치해도 조회 대상에 포함됨.
     * 여러 소속 경로 사이의 모순은 Service에서 추가 검증해야 함.
     *
     * nativeQuery = true:
     * 엔티티 필드명이 아닌 실제 DB 테이블·컬럼명을 사용하는 SQL.
     *
     * a.*:
     * accounts의 모든 컬럼을 조회하여 Account 엔티티로 매핑.
     */
    @Query(value = """
            SELECT a.account_id
            FROM accounts a
            WHERE (
                -- 1. 운영 계열사에 직접 소속된 계정
                a.operating_company_id = :operatorId

                -- 2. 해당 운영 계열사 산하 업체에 소속된 계정
                OR EXISTS (
                    SELECT 1
                    FROM companies c
                    WHERE c.company_id = a.company_id
                      AND c.operating_company_id = :operatorId
                )

                -- 3. 해당 운영 계열사 산하 업체가 운영하는 허브 소속 계정
                OR EXISTS (
                    SELECT 1
                    FROM hubs h
                    JOIN companies c
                      ON c.company_id = h.company_id
                    WHERE h.hub_id = a.hub_id
                      AND c.operating_company_id = :operatorId
                )

                -- 4. 해당 운영 계열사에 소속된 지역센터의 계정
                OR EXISTS (
                    SELECT 1
                    FROM logistics_centers lc
                    WHERE lc.center_id = a.center_id
                      AND lc.operating_company_id = :operatorId
                )
            )
            ORDER BY a.account_id
            """, nativeQuery = true)
    List<Long> findAccountIdsInOperatingCompany(
            // 메서드 인자를 SQL의 :operatorId에 바인딩
            @Param("operatorId") Long operatingCompanyId
    );

    // 통합(26.10.26): Account 에 @Formula(scopeCenterId) 가 있어 native "SELECT a.*" 엔티티 매핑 대신 ID 로 조회
    default List<Account> findAccountsInOperatingCompany(Long operatingCompanyId) {
        List<Long> ids = findAccountIdsInOperatingCompany(operatingCompanyId);
        java.util.Map<Long, Account> byId = new java.util.HashMap<>();
        findAllById(ids).forEach(a -> byId.put(a.getAccountId(), a));
        List<Account> out = new java.util.ArrayList<>();
        for (Long id : ids) { Account a = byId.get(id); if (a != null) out.add(a); }
        return out;
    }

    /*
     * 특정 계정이 지정한 운영 계열사에 연결되어 있는지 확인
     *
     * accountId: 조회·수정·삭제하려는 대상 계정 ID
     * operatingCompanyId: 로그인한 관리자의 운영 계열사 ID
     *
     * SELECT EXISTS:
     * 조건을 만족하는 계정이 있으면 true, 없으면 false 반환.
     *
     * 대상 계정 ID가 일치하면서,
     * 직접·업체·허브·센터 연결 중 하나가 같은 계열사여야 true.
     *
     * 이 메서드는 소속 연결만 확인함.
     * 호출자의 역할, 변경 후 소속, 소속 간 일관성은 Service에서 검사.
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM accounts a
                WHERE a.account_id = :accountId
                  AND (
                      -- 1. 운영 계열사 직접 소속
                      a.operating_company_id = :operatorId

                      -- 2. 산하 업체 소속
                      OR EXISTS (
                          SELECT 1
                          FROM companies c
                          WHERE c.company_id = a.company_id
                            AND c.operating_company_id = :operatorId
                      )

                      -- 3. 산하 업체가 운영하는 허브 소속
                      OR EXISTS (
                          SELECT 1
                          FROM hubs h
                          JOIN companies c
                            ON c.company_id = h.company_id
                          WHERE h.hub_id = a.hub_id
                            AND c.operating_company_id = :operatorId
                      )

                      -- 4. 산하 지역센터 소속
                      OR EXISTS (
                          SELECT 1
                          FROM logistics_centers lc
                          WHERE lc.center_id = a.center_id
                            AND lc.operating_company_id = :operatorId
                      )
                  )
            )
            """, nativeQuery = true)
    boolean existsAccountInOperatingCompany(
            @Param("accountId") Long accountId,
            @Param("operatorId") Long operatingCompanyId
    );
}