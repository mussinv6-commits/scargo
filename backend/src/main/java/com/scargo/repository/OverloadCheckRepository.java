package com.scargo.repository;

import com.scargo.entity.OverloadCheck;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OverloadCheckRepository
        extends JpaRepository<OverloadCheck, Long> {

    // 차량 번호 완전 일치 목록 조회
    List<OverloadCheck> findByVehicleNo(String vehicleNo);

    // 차량 번호 기준 가장 최근 과적 검사 조회
    Optional<OverloadCheck> findTopByVehicleNoOrderByCheckedAtDesc(
            String vehicleNo
    );

    // 차량 번호 부분 검색
    Page<OverloadCheck> findByVehicleNoContaining(
            String vehicleNo,
            Pageable pageable
    );

    // 컨테이너 번호 기준 조회
    List<OverloadCheck> findByContainerNo(String containerNo);

    // 과적 차량 조회
    Page<OverloadCheck> findByIsViolationTrue(Pageable pageable);

    // 최종 미통과 차량 조회
    Page<OverloadCheck> findByIsPassedFalse(Pageable pageable);

    // 특정 기간 검사 이력 조회
    Page<OverloadCheck> findByCheckedAtBetween(
            OffsetDateTime start,
            OffsetDateTime end,
            Pageable pageable
    );

    // 동일한 ENTRY gateLog가 이미 계량됐는지 확인
    boolean existsByGateLogId(Long gateLogId);

    // ENTRY gateLog에 연결된 계량 결과 조회
    Optional<OverloadCheck> findByGateLogId(Long gateLogId);

    // 오늘 계량 기록 최신순 50건 조회
    List<OverloadCheck>
    findTop50ByCheckedAtGreaterThanEqualOrderByCheckIdDesc(
            OffsetDateTime since
    );

    // checked_at 값이 NULL이면 현재 시각으로 설정
    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query(
            value =
                    "UPDATE overload_checks "
                    + "SET checked_at = now() "
                    + "WHERE check_id = :checkId "
                    + "AND checked_at IS NULL",
            nativeQuery = true
    )
    int stampCheckedAtIfMissing(
            @Param("checkId") Long checkId
    );
}