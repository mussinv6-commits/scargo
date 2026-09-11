package com.scargo.repository;

import com.scargo.entity.LoadingLocation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoadingLocationRepository extends JpaRepository<LoadingLocation, Long> {
}