package com.scargo.repository;

import com.scargo.entity.ContainerInspectionAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ContainerInspectionAuditRepository extends JpaRepository<ContainerInspectionAudit, Long> {

    List<ContainerInspectionAudit> findByInspectionIdInOrderByCreatedAtAscAuditIdAsc(Collection<Long> inspectionIds);
}
