package com.scargo.amr.repository;

import com.scargo.amr.entity.HubVehicleLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface HubVehicleLogRepository extends JpaRepository<HubVehicleLog, Long> {
    List<HubVehicleLog> findByHubIdOrderByLogIdDesc(Long hubId, Pageable pageable);
    List<HubVehicleLog> findByHubIdAndCreatedAtAfterOrderByLogIdAsc(Long hubId, OffsetDateTime after);
}
