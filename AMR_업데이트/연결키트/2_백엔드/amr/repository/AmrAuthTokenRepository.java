package com.scargo.amr.repository;

import com.scargo.amr.entity.AmrAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface AmrAuthTokenRepository extends JpaRepository<AmrAuthToken, String> {

    @Modifying
    @Query("update AmrAuthToken t set t.revoked = true where t.revoked = false")
    int revokeAll();

    @Modifying
    @Query("delete from AmrAuthToken t where t.expiresAt < :before")
    int deleteExpired(@Param("before") OffsetDateTime before);
}
