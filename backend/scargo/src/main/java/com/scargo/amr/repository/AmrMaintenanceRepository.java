package com.scargo.amr.repository;

import com.scargo.amr.entity.AmrMaintenance;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AmrMaintenanceRepository extends JpaRepository<AmrMaintenance, Long> {
    // 진행 중인 정비 (예약 · 정비 중) 중 가장 최근 것
    Optional<AmrMaintenance> findFirstByStatusInOrderByMaintIdDesc(Collection<String> statuses);
    Optional<AmrMaintenance> findFirstByRobotIdAndStatusInOrderByMaintIdDesc(Long robotId, Collection<String> statuses);
    List<AmrMaintenance> findAllByOrderByMaintIdDesc(Pageable pageable);
    // 26.10.22 허브별 조회 (허브의 로봇들)
    Optional<AmrMaintenance> findFirstByRobotIdInAndStatusInOrderByMaintIdDesc(Collection<Long> robotIds, Collection<String> statuses);
    List<AmrMaintenance> findByRobotIdInOrderByMaintIdDesc(Collection<Long> robotIds, Pageable pageable);
    // 26.11.02 예지보전 성과 (이번 달): AI 예측으로 잡힌 정비 중 끝난 건
    @Query("select count(m) from AmrMaintenance m where m.robotId in :ids and m.status = 'DONE' and m.predictionId is not null and m.completedAt >= :from and m.completedAt < :to")
    long countPreemptiveDone(@Param("ids") Collection<Long> ids, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
    // 26.11.02 예고 없는 고장: 예측 없이 생긴 고장 정비(CORRECTIVE) 건 (취소 제외)
    @Query("select count(m) from AmrMaintenance m where m.robotId in :ids and m.maintType = 'CORRECTIVE' and m.predictionId is null and m.status <> 'CANCELED' and m.scheduledAt >= :from and m.scheduledAt < :to")
    long countUnplanned(@Param("ids") Collection<Long> ids, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to);
}
