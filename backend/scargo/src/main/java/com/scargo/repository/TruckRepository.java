package com.scargo.repository;

import com.scargo.entity.Truck;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TruckRepository extends JpaRepository<Truck, String> {

    // 26.10.22 추가: 매핑 변경 시 같은 차량에 컨테이너가 동시에 두 개 배정되지 않도록 행 잠금 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Truck t WHERE t.vehicleNo = :vehicleNo")
    Optional<Truck> findForUpdate(@Param("vehicleNo") String vehicleNo);

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

    // 26.09.21 추가: 고정형(지입차) 배정 조회 - 특정 기사 계정에게 배정된 차량(있으면 1건)
    // 26.10.20: 통합 DB 는 기사 1명에 차량 여러 대 배정 가능(driver01=3대) → 첫 번째 차량 사용. TODO: 주 차량 지정 규칙 확정
    Optional<Truck> findFirstByAssignedDriver_AccountIdOrderByVehicleNoAsc(Long accountId);
    default Optional<Truck> findByAssignedDriver_AccountId(Long accountId) {
        return findFirstByAssignedDriver_AccountIdOrderByVehicleNoAsc(accountId);
    }

    // 26.09.21 추가: 이미 다른 차량에 배정된 기사인지 중복 배정 방지용 체크
    boolean existsByAssignedDriver_AccountId(Long accountId);
}