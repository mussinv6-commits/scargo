package com.scargo.repository;

import com.scargo.entity.TireManagement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TireManagementRepository extends JpaRepository<TireManagement, Long> {

    //특정 차량의 현재 사용 중(ACTIVE)인 타이어 목록 조회
    List<TireManagement> findByVehicleNoAndStatus(String vehicleNo, String status);

    //특정 차량의 특정 축 위치에 장착된 타이어 조회
    TireManagement findByVehicleNoAndAxlePositionAndStatus(String vehicleNo, String axlePosition, String status);
}