package com.scargo.ops.repository;

import com.scargo.ops.entity.OpsException;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface OpsExceptionRepository extends JpaRepository<OpsException, Long> {
    List<OpsException> findAllByOrderByOccurredAtDesc();
    List<OpsException> findByCenterIdOrderByOccurredAtDesc(Long centerId);
    List<OpsException> findByAssignmentIdInOrderByOccurredAtDesc(Collection<Long> assignmentIds);
    boolean existsBySourceTableAndSourceKeyAndExceptionType(String t, String key, String type);
    List<OpsException> findBySourceTableAndSourceKey(String t, String key);
}
