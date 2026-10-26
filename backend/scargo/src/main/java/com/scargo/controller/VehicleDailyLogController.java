package com.scargo.controller;

import com.scargo.dto.VehicleDailyLogCreateRequest;
import com.scargo.dto.VehicleDailyLogResponse;
import com.scargo.dto.VehicleDailyLogUpdateRequest;
import com.scargo.security.TruckAccessPolicy;
import com.scargo.service.VehicleDailyLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 26.10.22 수정: 권한 검사 추가 (기존에는 로그인만 하면 아무 차량의 기록이나 등록·수정·삭제 가능)
//  - 등록·수정·삭제: 배정 기사(본인 차량), 운송사 담당자(자기 업체 차량), 관리자
//  - 조회: 위 권한 + 관제사(관리 구간의 차량)
//  - 차량 범위는 TruckAccessPolicy 기준
@RestController
@RequestMapping("/api/vehicle-logs")
@RequiredArgsConstructor
public class VehicleDailyLogController {

    private final VehicleDailyLogService dailyLogService;
    private final TruckAccessPolicy truckAccessPolicy;

    // 1. 일일 운행 기록 등록
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED', 'CONTAINER_DRIVER', 'BRANCH_DRIVER')")
    public ResponseEntity<VehicleDailyLogResponse> createDailyLog(
            @Valid @RequestBody VehicleDailyLogCreateRequest request,
            Authentication authentication) {
        truckAccessPolicy.requireRead(authentication, request.getVehicleNo());
        VehicleDailyLogResponse response = dailyLogService.createDailyLog(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. 특정 차량의 전체 운행 기록 조회
    @GetMapping("/vehicle/{vehicleNo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'HUB_OP', 'CENTER_OP', 'CONTAINER_DRIVER', 'BRANCH_DRIVER', 'CORPORATE_APPROVED')")
    public ResponseEntity<List<VehicleDailyLogResponse>> getLogsByVehicleNo(
            @PathVariable("vehicleNo") String vehicleNo,
            Authentication authentication) {
        truckAccessPolicy.requireRead(authentication, vehicleNo);
        List<VehicleDailyLogResponse> responses = dailyLogService.getLogsByVehicleNo(vehicleNo);
        return ResponseEntity.ok(responses);
    }

    // 3. 운행 기록 수정
    @PutMapping("/{logId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED', 'CONTAINER_DRIVER', 'BRANCH_DRIVER')")
    public ResponseEntity<VehicleDailyLogResponse> updateDailyLog(
            @PathVariable("logId") Long logId,
            @RequestBody VehicleDailyLogUpdateRequest request,
            Authentication authentication) {
        truckAccessPolicy.requireRead(authentication, dailyLogService.getVehicleNo(logId));
        VehicleDailyLogResponse response = dailyLogService.updateDailyLog(logId, request);
        return ResponseEntity.ok(response);
    }

    // 4. 운행 기록 삭제
    @DeleteMapping("/{logId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED', 'CONTAINER_DRIVER', 'BRANCH_DRIVER')")
    public ResponseEntity<Void> deleteDailyLog(
            @PathVariable("logId") Long logId,
            Authentication authentication) {
        truckAccessPolicy.requireRead(authentication, dailyLogService.getVehicleNo(logId));
        dailyLogService.deleteDailyLog(logId);
        return ResponseEntity.ok().build();
    }
}
