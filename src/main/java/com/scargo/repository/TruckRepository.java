package com.scargo.repository;

import com.scargo.entity.Truck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TruckRepository extends JpaRepository<Truck, String> {
    List<Truck> findByCompanyId(Long companyId);
}