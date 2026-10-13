package com.scargo.repository;

import com.scargo.dto.LoadingRecordSearchCondition;
import com.scargo.entity.LoadingRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LoadingRecordRepositoryCustom {
    Page<LoadingRecord> searchLoadingRecords(LoadingRecordSearchCondition condition, Pageable pageable);
}