package com.scargo.ops.repository;

import com.scargo.ops.entity.LogisticsCenter;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LogisticsCenterRepository extends JpaRepository<LogisticsCenter, Long> {
}
