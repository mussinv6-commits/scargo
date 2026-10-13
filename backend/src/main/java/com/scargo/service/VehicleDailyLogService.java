package com.scargo.service;

import com.scargo.dto.VehicleDailyLogCreateRequest;
import com.scargo.dto.VehicleDailyLogResponse;
import com.scargo.dto.VehicleDailyLogUpdateRequest;
import com.scargo.entity.VehicleDailyLog;
import com.scargo.repository.VehicleDailyLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleDailyLogService {

    private final VehicleDailyLogRepository logRepository;
    // private final NotificationService notificationService; // 알림 연동용 서비스 (필요시 주석 해제)

    // 1. 일일 운행 기록 생성 (누적 거리 자동 계산 및 10만 km 알람 체크)
    @Transactional
    public VehicleDailyLogResponse createDailyLog(VehicleDailyLogCreateRequest request) {
        String vehicleNo = request.getVehicleNo();
        int todayDistance = request.getDailyDistance();

        // 1) 해당 차량의 가장 최근 운행 기록 조회
        VehicleDailyLog lastLog = logRepository
                .findFirstByVehicleNoOrderByDrivingDateDesc(vehicleNo)
                .orElse(null);

        // 2) 직전 누적 주행거리 계산 (기록이 없으면 0)
        int previousMileage = (lastLog != null) ? lastLog.getAccumulatedMileage() : 0;

        // 3) 새로운 누적 주행거리 계산
        int newAccumulatedMileage = previousMileage + todayDistance;

        // 4) 10만 단위 돌파 체크 (알람 트리거)
        checkTireMilestoneAlarm(vehicleNo, previousMileage, newAccumulatedMileage);

        // 5) 엔티티 생성 및 저장
        VehicleDailyLog newLog = VehicleDailyLog.builder()
                .vehicleNo(vehicleNo)
                .drivingDate(request.getDrivingDate())
                .dailyDistance(todayDistance)
                .accumulatedMileage(newAccumulatedMileage)
                .memo(request.getMemo())
                .build();

        VehicleDailyLog savedLog = logRepository.save(newLog);

        return convertToResponse(savedLog);
    }

    // 2. 특정 차량의 전체 운행 기록 조회
    public List<VehicleDailyLogResponse> getLogsByVehicleNo(String vehicleNo) {
        return logRepository.findByVehicleNoOrderByDrivingDateDesc(vehicleNo)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    //3. 운행 기록 수정 (이후 날짜 기록들의 누적 거리 연쇄 재계산 포함)
    @Transactional
    public VehicleDailyLogResponse updateDailyLog(Long logId, VehicleDailyLogUpdateRequest request) {
        VehicleDailyLog targetLog = logRepository.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("해당 운행 기록을 찾을 수 없습니다. ID: " + logId));

        String vehicleNo = targetLog.getVehicleNo();
        boolean needsRecalculation = false;

     // 1) 기본 정보 업데이트 (일자, 메모)
        if (request.getDrivingDate() != null && !request.getDrivingDate().equals(targetLog.getDrivingDate())) {
            targetLog.setDrivingDate(request.getDrivingDate()); 
            needsRecalculation = true;
        }
        if (request.getMemo() != null) {
            targetLog.setMemo(request.getMemo());
        }

        // 2) 일일 주행 거리 변경 체크 (Integer 타입은 != 비교가 안전합니다)
        if (request.getDailyDistance() != null && !request.getDailyDistance().equals(targetLog.getDailyDistance())) {
            targetLog.setDailyDistance(request.getDailyDistance());
            needsRecalculation = true;
        }

        // 3) 변경사항이 있다면 이후 날짜의 누적 거리 연쇄 재계산 수행
        if (needsRecalculation) {
            recalculateSubsequentLogs(vehicleNo, targetLog.getDrivingDate());
        }

        return convertToResponse(targetLog);
    }

    //4. 운행 기록 삭제 (삭제 후 이후 날짜 기록들의 누적 거리 연쇄 재계산 포함)
    @Transactional
    public void deleteDailyLog(Long logId) {
        VehicleDailyLog dailyLog = logRepository.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("해당 운행 기록을 찾을 수 없습니다. ID: " + logId));

        String vehicleNo = dailyLog.getVehicleNo();
        var drivingDate = dailyLog.getDrivingDate();

        // 1) 삭제 실행
        logRepository.delete(dailyLog);

        // 2) 삭제된 날짜 이후의 기록들 누적 거리 재정렬
        recalculateSubsequentLogs(vehicleNo, drivingDate);
    }

    //특정 기준일 이후의 모든 누적 거리를 순차적으로 재계산하는 공통 메서드
    private void recalculateSubsequentLogs(String vehicleNo, java.time.LocalDate baseDate) {
        // 기준일 직전의 가장 최근 로그 조회 (없으면 기준점 0부터 시작)
        VehicleDailyLog previousLog = logRepository
                .findFirstByVehicleNoAndDrivingDateLessThanOrderByDrivingDateDesc(vehicleNo, baseDate)
                .orElse(null);

        int currentRunningMileage = (previousLog != null) ? previousLog.getAccumulatedMileage() : 0;

        // 기준일 이상(포함)인 이후의 모든 로그들을 날짜 오름차순으로 조회
        List<VehicleDailyLog> subsequentLogs = logRepository
                .findByVehicleNoAndDrivingDateGreaterThanEqualOrderByDrivingDateAsc(vehicleNo, baseDate);

        for (VehicleDailyLog logItem : subsequentLogs) {
            currentRunningMileage += logItem.getDailyDistance();
            logItem.setAccumulatedMileage(currentRunningMileage);
        }
    }

    //10만 단위 경계값 돌파 확인 및 알람 메서드
    private void checkTireMilestoneAlarm(String vehicleNo, int prevMileage, int newMileage) {
        int prevMilestone = prevMileage / 100000;
        int newMilestone = newMileage / 100000;

        if (newMilestone > prevMilestone) {
            int reachedMileage = newMilestone * 100000;
            log.info("🚨 [알람] 차량 [{}] 누적 주행거리 {}km 돌파! 타이어 점검 및 교체를 확인하세요.", vehicleNo, reachedMileage);
            // TODO: 실제 알림톡 또는 푸시 발송 서비스 호출
        }
    }

    // Entity -> Response DTO 변환 헬퍼 메서드
    private VehicleDailyLogResponse convertToResponse(VehicleDailyLog entity) {
        return VehicleDailyLogResponse.builder()
                .logId(entity.getLogId())
                .vehicleNo(entity.getVehicleNo())
                .drivingDate(entity.getDrivingDate())
                .dailyDistance(entity.getDailyDistance())
                .accumulatedMileage(entity.getAccumulatedMileage())
                .memo(entity.getMemo())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}