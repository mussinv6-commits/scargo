package com.scargo.ops.repository;

import com.scargo.ops.entity.BranchUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BranchUnitRepository extends JpaRepository<BranchUnit, String> {
    List<BranchUnit> findByAssignmentId(Long id);
}
