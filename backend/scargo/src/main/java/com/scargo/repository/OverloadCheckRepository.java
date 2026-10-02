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
public interface OverloadCheckRepository extends JpaRepository<OverloadCheck, Long> {

    // 차량 번호 완전 일치 목록 조회
    List<OverloadCheck> findByVehicleNo(String vehicleNo);

    // 차량 번호 기준 가장 최근 과적 검사 1건 조회
    Optional<OverloadCheck> findTopByVehicleNoOrderByCheckedAtDesc(String vehicleNo);

    // 차량 번호 부분 검색 (페이징)
    Page<OverloadCheck> findByVehicleNoContaining(String vehicleNo, Pageable pageable);

    // 컨테이너 번호로 과적 검사 기록 목록 조회
    List<OverloadCheck> findByContainerNo(String containerNo);

    // 규정 위반 여부별 목록 조회 (페이징)
    Page<OverloadCheck> findByIsViolationTrue(Pageable pageable);

    // 최종 통과 여부별 불합격 목록 조회 (페이징)
    Page<OverloadCheck> findByIsPassedFalse(Pageable pageable);

    // 특정 기간 내 과적 검사 이력 조회 (페이징)
    Page<OverloadCheck> findByCheckedAtBetween(
            OffsetDateTime start,
            OffsetDateTime end,
            Pageable pageable
    );
    
 // 26.10.01 추가(계중대 정식화): 같은 게이트 통과 기록을 두 번 계량하지 않도록 확인
    boolean existsByGateLogId(Long gateLogId);

    // 26.10.02 추가: 이번 방문(입차 후 일정 시간) 안에 이미 계량한 차량인지 - 출차 때 다시 계량하지 않도록
    boolean existsByVehicleNoAndCheckedAtGreaterThanEqual(String vehicleNo, OffsetDateTime since);

    // 26.10.01 추가: 계중대 화면의 "오늘 계량 기록" (최신순 50건)
    List<OverloadCheck> findTop50ByCheckedAtGreaterThanEqualOrderByCheckIdDesc(OffsetDateTime since);

    // 26.10.01 추가: checked_at 은 insertable=false(DB 기본값 사용)인데, 테이블을 JPA(ddl-auto)가 만든 경우
    // DB 기본값이 없어 NULL 로 남는다 → 계량 저장 직후 비어 있으면 현재 시각으로 채운다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE overload_checks SET checked_at = now() WHERE check_id = :checkId AND checked_at IS NULL",
           nativeQuery = true)
    int stampCheckedAtIfMissing(@Param("checkId") Long checkId);

    
}