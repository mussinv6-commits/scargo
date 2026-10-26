package com.scargo.ops.repository;

import com.scargo.ops.entity.ShipmentLeg;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ShipmentLegRepository extends JpaRepository<ShipmentLeg, Long> {
    List<ShipmentLeg> findByLegTypeAndDriverAccountIdOrderByAssignmentIdDesc(String legType, Long driver);
    List<ShipmentLeg> findByLegTypeAndVehicleNoInOrderByAssignmentIdDesc(String legType, java.util.Collection<String> vehicleNos);
    List<ShipmentLeg> findByLegTypeAndFromCenterId(String legType, Long fromCenterId);
}
