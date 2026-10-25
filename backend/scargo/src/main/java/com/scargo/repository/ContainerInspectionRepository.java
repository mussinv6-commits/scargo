package com.scargo.repository;

import com.scargo.entity.ContainerInspection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContainerInspectionRepository extends JpaRepository<ContainerInspection, Long> {

    // 상태 변경 · 재점검 전에 행을 잠근다 (동시 확정 · 재점검 방지)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ContainerInspection c WHERE c.inspectionId = :id")
    Optional<ContainerInspection> findForUpdate(@Param("id") Long id);

    List<ContainerInspection> findByPreviousInspectionId(Long previousInspectionId);

    boolean existsByPreviousInspectionIdAndRecordStatusNot(Long previousInspectionId, String recordStatus);
}
