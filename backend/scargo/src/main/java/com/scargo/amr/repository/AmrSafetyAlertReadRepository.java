package com.scargo.amr.repository;

import com.scargo.amr.entity.AmrSafetyAlertRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface AmrSafetyAlertReadRepository extends JpaRepository<AmrSafetyAlertRead, AmrSafetyAlertRead.Pk> {
    @Query("SELECT r.alertId FROM AmrSafetyAlertRead r WHERE r.accountId = :accountId AND r.alertId IN :ids")
    Set<Long> findReadAlertIds(@Param("accountId") Long accountId, @Param("ids") Collection<Long> ids);

    List<AmrSafetyAlertRead> findByAccountId(Long accountId);

    boolean existsByAlertIdAndAccountId(Long alertId, Long accountId);
}
