package com.scargo.repository;

import com.scargo.entity.Truck;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TruckRepository extends JpaRepository<Truck, String> {

    // 특정 업체의 모든 차량 조회
    List<Truck> findByCompany_CompanyId(Long companyId);

    // 특정 업체의 특정 상태 차량 조회 (예: INSIDE 상태)
    List<Truck> findByCompany_CompanyIdAndStatus(Long companyId, String status);

    // 특정 상태의 차량 목록을 페이징 처리하여 조회 (프론트엔드 목록용)
    Page<Truck> findByStatus(String status, Pageable pageable);

    // 차량 번호 존재 여부 확인 (게이트 진입 시 검증용)
    boolean existsByVehicleNo(String vehicleNo);

    //트레일러 번호 기반 조회 (단건)
    Optional<Truck> findByTrailerNo(String trailerNo);

    //차종별 차량 페이징 조회
    Page<Truck> findByTruckType(String truckType, Pageable pageable);
    
 // [추가] 차량 번호로 단건 조회 (Optional 반환)
    Optional<Truck> findByVehicleNo(String vehicleNo);
}