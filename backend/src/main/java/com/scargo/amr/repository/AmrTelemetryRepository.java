package com.scargo.amr.repository;

import com.scargo.amr.entity.AmrTelemetry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface AmrTelemetryRepository extends JpaRepository<AmrTelemetry, Long> {
    List<AmrTelemetry> findByRobotIdAndRecordedAtAfterOrderByRecordedAtAsc(Long robotId, OffsetDateTime after);
}
