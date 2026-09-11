package com.scargo.repository;

import com.scargo.entity.Truck;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TruckRepository extends JpaRepository<Truck, String> {
    // PK가 String 타입(vehicleNo)이므로 JpaRepository<Truck, String>
}