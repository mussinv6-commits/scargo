package com.scargo.ops.repository;

import com.scargo.ops.entity.HubDispatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HubDispatchRepository extends JpaRepository<HubDispatch, Long> {
    java.util.Optional<HubDispatch> findFirstByAssignmentId(Long id);
}
