package com.scargo.repository;

import com.scargo.entity.VehicleChecklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface VehicleChecklistRepository extends JpaRepository<VehicleChecklist, Long> {

    // 전체 점검 이력 조회 (최신 점검일순 정렬)
    List<VehicleChecklist> findAllByOrderByInspectionDateDesc();

    // 특정 차량의 전체 점검 이력 조회 (최신 점검일순 정렬)
    List<VehicleChecklist> findByVehicleNoOrderByInspectionDateDesc(String vehicleNo);

    // 특정 점검자가 작성한 점검 이력 조회 (최신 점검일순 정렬)
    List<VehicleChecklist> findByInspectorAccountIdOrderByInspectionDateDesc(Long inspectorAccountId);

    // 특정 차량의 특정 일자 점검 이력 조회
    List<VehicleChecklist> findByVehicleNoAndInspectionDate(String vehicleNo, LocalDate inspectionDate);

    // 특정 차량의 특정 기간 점검 이력 조회
    @Query("SELECT c FROM VehicleChecklist c WHERE c.vehicleNo = :vehicleNo AND c.inspectionDate BETWEEN :startDate AND :endDate ORDER BY c.inspectionDate DESC")
    List<VehicleChecklist> findByPeriod(
            @Param("vehicleNo") String vehicleNo,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // 특정 차량이 해당 회사의 소속이 맞는지 확인 (t.company.id로 수정)
    @Query("SELECT COUNT(t) > 0 FROM Truck t WHERE t.vehicleNo = :vehicleNo AND t.company.id = :companyId")
    boolean existsByVehicleNoAndCompanyId(@Param("vehicleNo") String vehicleNo, @Param("companyId") Long companyId);

    // 특정 점검 기록이 해당 회사의 소속 차량인지 확인 (t.company.id로 수정)
    @Query("SELECT COUNT(c) > 0 FROM VehicleChecklist c " +
           "JOIN Truck t ON c.vehicleNo = t.vehicleNo " +
           "WHERE c.inspectionId = :inspectionId AND t.company.id = :companyId")
    boolean existsByIdAndCompanyId(@Param("inspectionId") Long inspectionId, @Param("companyId") Long companyId);

    // 특정 회사의 소속된 모든 차량의 점검 이력 전체 조회용 (t.company.id로 수정)
    @Query("SELECT c FROM VehicleChecklist c " +
           "JOIN Truck t ON c.vehicleNo = t.vehicleNo " +
           "WHERE t.company.id = :companyId ORDER BY c.inspectionDate DESC")
    List<VehicleChecklist> findAllByCompanyId(@Param("companyId") Long companyId);
}