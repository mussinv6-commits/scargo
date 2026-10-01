package com.scargo.repository;

import com.scargo.entity.LoadingRecord;
import com.scargo.entity.LoadingRecord.LoadingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoadingRecordRepository extends JpaRepository<LoadingRecord, Long>, LoadingRecordRepositoryCustom {

    // 특정 차량(vehicleNo)의 적재 기록 목록 조회 (페이징 및 리스트)
    Page<LoadingRecord> findByTruck_VehicleNo(String vehicleNo, Pageable pageable);
    List<LoadingRecord> findByTruck_VehicleNo(String vehicleNo);

    // 특정 컨테이너(containerNo)의 적재 기록 목록 조회 (페이징 및 리스트)
    Page<LoadingRecord> findByContainer_ContainerNo(String containerNo, Pageable pageable);
    List<LoadingRecord> findByContainer_ContainerNo(String containerNo);

    // 특정 장소(locationId)의 적재 기록 목록 조회 (페이징 및 리스트)
    Page<LoadingRecord> findByLocation_LocationId(Long locationId, Pageable pageable);
    List<LoadingRecord> findByLocation_LocationId(Long locationId);

    // 특정 차량의 가장 최근 적재 기록 조회
    Optional<LoadingRecord> findFirstByTruck_VehicleNoOrderByRecordIdDesc(String vehicleNo);

    // 특정 컨테이너의 가장 최근 적재 기록 조회
    Optional<LoadingRecord> findFirstByContainer_ContainerNoOrderByRecordIdDesc(String containerNo);

    // 중복 검증용 (미완료 상태 건 존재 확인)
    boolean existsByContainer_ContainerNoAndStatusNot(String containerNo, LoadingStatus status);

    // 기간별 조회 (createdAt -> loadedAt으로 수정)
    Page<LoadingRecord> findByLoadedAtBetween(OffsetDateTime start, OffsetDateTime end, Pageable pageable);
}