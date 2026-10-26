package com.scargo.ops.repository;

import com.scargo.ops.entity.ItemVehicleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ItemVehicleAssignmentRepository extends JpaRepository<ItemVehicleAssignment, Long> {
    List<ItemVehicleAssignment> findByAssignmentId(Long id);
    java.util.Optional<ItemVehicleAssignment> findByItemIdAndAssignmentId(Long itemId, Long id);
}
