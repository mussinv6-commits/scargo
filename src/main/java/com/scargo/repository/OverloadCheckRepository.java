package com.scargo.repository;

import com.scargo.entity.OverloadCheck;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverloadCheckRepository extends JpaRepository<OverloadCheck, Long> {
}