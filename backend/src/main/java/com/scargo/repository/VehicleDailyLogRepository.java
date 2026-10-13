package com.scargo.repository;

import com.scargo.entity.VehicleDailyLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleDailyLogRepository extends JpaRepository<VehicleDailyLog, Long> {

    // 특정 차량의 전체 운행 일지 목록 조회 (최신 운행일 순)
    List<VehicleDailyLog> findByVehicleNoOrderByDrivingDateDesc(String vehicleNo);

    // 특정 차량의 가장 최근 운행 기록 1건 조회 (최근 누적 주행거리 확인용)
    Optional<VehicleDailyLog> findFirstByVehicleNoOrderByDrivingDateDesc(String vehicleNo);
    
    // 특정 날짜 이후(이상/초과)의 운행 기록 조회 (날짜 오름차순)
    List<VehicleDailyLog> findByVehicleNoAndDrivingDateGreaterThanEqualOrderByDrivingDateAsc(String vehicleNo, LocalDate drivingDate);
    List<VehicleDailyLog> findByVehicleNoAndDrivingDateGreaterThanOrderByDrivingDateAsc(String vehicleNo, LocalDate drivingDate);

    // 특정 날짜 이전 중 가장 가까운 운행 기록 1건 조회 (수정 시 직전 누적 거리 참고용)
    Optional<VehicleDailyLog> findFirstByVehicleNoAndDrivingDateLessThanOrderByDrivingDateDesc(String vehicleNo, LocalDate drivingDate);
}