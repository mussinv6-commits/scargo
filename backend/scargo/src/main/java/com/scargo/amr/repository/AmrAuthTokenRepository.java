package com.scargo.amr.repository;

import com.scargo.amr.entity.AmrAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;

public interface AmrAuthTokenRepository extends JpaRepository<AmrAuthToken, String> {

    @Modifying
    @Query("update AmrAuthToken t set t.revoked = true where t.revoked = false")
    int revokeAll();
    // 26.10.22 허브 관제사의 "모든 앱 로그아웃": 자기 허브 키로 만든 출입증만 무효
    @Modifying
    @Query("update AmrAuthToken t set t.revoked = true where t.revoked = false and t.keyId in :keyIds")
    int revokeByKeyIds(@Param("keyIds") Collection<Long> keyIds);

    @Modifying
    @Query("delete from AmrAuthToken t where t.expiresAt < :before")
    int deleteExpired(@Param("before") OffsetDateTime before);
}
