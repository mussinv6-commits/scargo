package com.scargo.amr.repository;

import com.scargo.amr.entity.AmrTaskLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface AmrTaskLogRepository extends JpaRepository<AmrTaskLog, Long> {
    List<AmrTaskLog> findByHubIdAndFinishedAtAfter(Long hubId, OffsetDateTime after);
    long countByHubIdAndFinishedAtBetween(Long hubId, OffsetDateTime from, OffsetDateTime to);
}
