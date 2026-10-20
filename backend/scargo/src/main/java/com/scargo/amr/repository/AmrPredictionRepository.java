package com.scargo.amr.repository;

import com.scargo.amr.entity.AmrPrediction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmrPredictionRepository extends JpaRepository<AmrPrediction, Long> {
}
