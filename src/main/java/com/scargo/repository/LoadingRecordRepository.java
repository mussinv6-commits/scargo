package com.scargo.repository;

import com.scargo.entity.LoadingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoadingRecordRepository extends JpaRepository<LoadingRecord, Long> {
}