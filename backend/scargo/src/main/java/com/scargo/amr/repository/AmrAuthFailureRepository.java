package com.scargo.amr.repository;

import com.scargo.amr.entity.AmrAuthFailure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface AmrAuthFailureRepository extends JpaRepository<AmrAuthFailure, Long> {
    long countByOccurredAtAfter(OffsetDateTime after);
    List<AmrAuthFailure> findTop50ByOrderByFailureIdDesc();
}
