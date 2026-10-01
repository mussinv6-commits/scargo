package com.scargo.service;

import com.scargo.Enum.NotificationType;
import com.scargo.dto.NotificationCreateRequest;
import com.scargo.dto.OverloadCheckCreateRequest;
import com.scargo.dto.OverloadCheckResponse;
import com.scargo.dto.OverloadCheckUpdateRequest;
import com.scargo.entity.OverloadCheck;
import com.scargo.repository.OverloadCheckRepository;
import com.scargo.repository.TruckRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OverloadService {

    private final OverloadCheckRepository overloadCheckRepository;
    private final NotificationService notificationService; // 알림 서비스 주입
    private final TruckRepository truckRepository; // 트럭 정보 조회를 위한 리포지토리 주입

 // 1. 과적 검사 기록 생성
    @Transactional
    public OverloadCheckResponse createCheck(OverloadCheckCreateRequest request) {
        OverloadCheck check = OverloadCheck.builder()
                .vehicleNo(request.getVehicleNo())
                .containerNo(request.getContainerNo())
                .usagePurpose(request.getUsagePurpose())
                .emptyVehicleWeight(request.getEmptyVehicleWeight())
                .totalWeight(request.getTotalWeight())
                .maxPayload(request.getMaxPayload())
                .vgmWeight(request.getVgmWeight())
                .tireCount(request.getTireCount())
                .vehicleAxleCount(request.getVehicleAxleCount())
                .axleCount(request.getAxleCount())
                .axle1Weight(request.getAxle1Weight())
                .axle2Weight(request.getAxle2Weight())
                .axle3Weight(request.getAxle3Weight())
                .axle4Weight(request.getAxle4Weight())
                .axle5Weight(request.getAxle5Weight())
                .axle6Weight(request.getAxle6Weight())
                .axle7Weight(request.getAxle7Weight())
                .axle8Weight(request.getAxle8Weight())
                .axle1WheelLeft(request.getAxle1WheelLeft())
                .axle1WheelRight(request.getAxle1WheelRight())
                .axle2WheelLeft(request.getAxle2WheelLeft())
                .axle2WheelRight(request.getAxle2WheelRight())
                .axle3WheelLeft(request.getAxle3WheelLeft())
                .axle3WheelRight(request.getAxle3WheelRight())
                .axle4WheelLeft(request.getAxle4WheelLeft())
                .axle4WheelRight(request.getAxle4WheelRight())
                .axle5WheelLeft(request.getAxle5WheelLeft())
                .axle5WheelRight(request.getAxle5WheelRight())
                .axle6WheelLeft(request.getAxle6WheelLeft())
                .axle6WheelRight(request.getAxle6WheelRight())
                .axle7WheelLeft(request.getAxle7WheelLeft())
                .axle7WheelRight(request.getAxle7WheelRight())
                .axle8WheelLeft(request.getAxle8WheelLeft())
                .axle8WheelRight(request.getAxle8WheelRight())
                // 26.09.21 수정: '(중간 축중 데이터 생략)' 플레이스홀더로 인해 axle2~8 중량/윤중 데이터가
                // 실제로는 전혀 저장되지 않던 문제를 복구함
                .isViolation(request.getIsViolation())
                .violationReason(request.getViolationReason())
                .retryCount(request.getRetryCount() != null ? request.getRetryCount() : 0)
                .isPassed(request.getIsPassed())
                .gateLogId(request.getGateLogId())     // 26.10.01 추가: 게이트 통과 기록 연결
                .stationCode(request.getStationCode()) // 26.10.01 추가: 계중대 코드
                .build();

        OverloadCheck savedCheck = overloadCheckRepository.save(check);

        // ==========================================
        // 2. [트리거] 과적 위반 또는 불합격 시 알림 자동 생성
        // ==========================================
        if (Boolean.TRUE.equals(savedCheck.getIsViolation()) || Boolean.FALSE.equals(savedCheck.getIsPassed())) {
            
            // 차량 번호(vehicleNo)를 이용해 해당 트럭의 소속 기업(companyId) 조회
        	Long targetCompanyId = truckRepository.findByVehicleNo(savedCheck.getVehicleNo())
        	        .map(truck -> truck.getCompany().getCompanyId()) 
        	        .orElse(null);

            NotificationCreateRequest notificationRequest = NotificationCreateRequest.builder()
                    .companyId(targetCompanyId) // 조회한 기업 ID 대입
                    .title("⚠️ 과적 단속/검사 경고")
                    .message(String.format("차량 [%s] 과적 검사 결과 위반/불합격 판정되었습니다. (사유: %s)", 
                            savedCheck.getVehicleNo(), 
                            savedCheck.getViolationReason() != null ? savedCheck.getViolationReason() : "기준 초과"))
                    .notificationType(NotificationType.OVERLOAD_WARNING) 
                    .referenceId(savedCheck.getCheckId()) // 스키마 PK 컬럼명에 맞춰 getCheckId() 사용
                    .build();

            notificationService.createNotification(notificationRequest);
        }

        return new OverloadCheckResponse(savedCheck);
    }

    // 2. 단건 조회
    public OverloadCheckResponse getCheckById(Long checkId) {
        OverloadCheck check = overloadCheckRepository.findById(checkId)
                .orElseThrow(() -> new IllegalArgumentException("해당 과적 검사 기록을 찾을 수 없습니다. id=" + checkId));
        return new OverloadCheckResponse(check);
    }

    // 3. 전체 목록 조회 (페이징)
    public Page<OverloadCheckResponse> getAllChecks(Pageable pageable) {
        return overloadCheckRepository.findAll(pageable)
                .map(OverloadCheckResponse::new);
    }

    // 4. 차량 번호별 검색 (페이징)
    public Page<OverloadCheckResponse> getChecksByVehicleNo(String vehicleNo, Pageable pageable) {
        return overloadCheckRepository.findByVehicleNoContaining(vehicleNo, pageable)
                .map(OverloadCheckResponse::new);
    }

    // 5. 위반(과적) 차량 목록 조회 (페이징)
    public Page<OverloadCheckResponse> getViolationChecks(Pageable pageable) {
        return overloadCheckRepository.findByIsViolationTrue(pageable)
                .map(OverloadCheckResponse::new);
    }

    // 6. 불합격(isPassed = false) 차량 목록 조회 (페이징)
    public Page<OverloadCheckResponse> getFailedChecks(Pageable pageable) {
        return overloadCheckRepository.findByIsPassedFalse(pageable)
                .map(OverloadCheckResponse::new);
    }

    // 7. 기간별 조회 (페이징)
    public Page<OverloadCheckResponse> getChecksBetween(OffsetDateTime start, OffsetDateTime end, Pageable pageable) {
        return overloadCheckRepository.findByCheckedAtBetween(start, end, pageable)
                .map(OverloadCheckResponse::new);
    }

    // 8. 재검증(Retry) 처리 - 감량 후 재측정 시 retryCount 증가 및 결과 반영
    @Transactional
    public OverloadCheckResponse retryCheck(Long checkId, OverloadCheckCreateRequest request) {
        OverloadCheck check = overloadCheckRepository.findById(checkId)
                .orElseThrow(() -> new IllegalArgumentException("해당 과적 검사 기록을 찾을 수 없습니다. id=" + checkId));

        int updatedRetryCount = (check.getRetryCount() != null ? check.getRetryCount() : 0) + 1;

        check.updateCorrection(
                request.getVehicleNo(),
                request.getContainerNo(),
                request.getUsagePurpose(),
                request.getIsViolation(),
                request.getViolationReason(),
                updatedRetryCount,
                request.getIsPassed()
        );

        return new OverloadCheckResponse(check);
    }

    // 9. 관리자 수동 오기 정정
    @Transactional
    public OverloadCheckResponse updateCheck(Long checkId, OverloadCheckUpdateRequest request) {
        OverloadCheck check = overloadCheckRepository.findById(checkId)
                .orElseThrow(() -> new IllegalArgumentException("해당 과적 검사 기록을 찾을 수 없습니다. id=" + checkId));

        check.updateCorrection(
                request.getVehicleNo(),
                request.getContainerNo(),
                request.getUsagePurpose(),
                request.getIsViolation(),
                request.getViolationReason(),
                request.getRetryCount(),
                request.getIsPassed()
        );

        return new OverloadCheckResponse(check);
    }

    // 10. 삭제
    @Transactional
    public void deleteCheck(Long checkId) {
        OverloadCheck check = overloadCheckRepository.findById(checkId)
                .orElseThrow(() -> new IllegalArgumentException("해당 과적 검사 기록을 찾을 수 없습니다. id=" + checkId));
        overloadCheckRepository.delete(check);
    }
}