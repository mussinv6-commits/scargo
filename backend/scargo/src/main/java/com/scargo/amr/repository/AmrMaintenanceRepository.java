package com.scargo.amr.repository;

import com.scargo.amr.entity.AmrMaintenance;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AmrMaintenanceRepository extends JpaRepository<AmrMaintenance, Long> {
    // 진행 중인 정비 (예약 · 정비 중) 중 가장 최근 것
    Optional<AmrMaintenance> findFirstByStatusInOrderByMaintIdDesc(Collection<String> statuses);
    Optional<AmrMaintenance> findFirstByRobotIdAndStatusInOrderByMaintIdDesc(Long robotId, Collection<String> statuses);
    List<AmrMaintenance> findAllByOrderByMaintIdDesc(Pageable pageable);
}
