package com.scargo.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.scargo.app.entity.VehicleSpec;

public interface VehicleSpecRepository extends JpaRepository<VehicleSpec, String> {
}
