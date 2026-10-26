package com.scargo.app.repository;

import com.scargo.app.entity.AppToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

public interface AppTokenRepository extends JpaRepository<AppToken, String> {

    @Modifying
    @Transactional
    @Query("delete from AppToken t where t.expiresAt < :now")
    int deleteExpired(@Param("now") OffsetDateTime now);
}
