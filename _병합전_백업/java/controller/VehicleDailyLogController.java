package com.scargo.controller;

import com.scargo.dto.VehicleDailyLogCreateRequest;
import com.scargo.dto.VehicleDailyLogResponse;
import com.scargo.dto.VehicleDailyLogUpdateRequest;
import com.scargo.service.VehicleDailyLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicle-logs")
@RequiredArgsConstructor
public class VehicleDailyLogController {

    private final VehicleDailyLogService dailyLogService;

    // 1. 일일 운행 기록 등록
    @PostMapping
    public ResponseEntity<VehicleDailyLogResponse> createDailyLog(
            @RequestBody VehicleDailyLogCreateRequest request) {
        VehicleDailyLogResponse response = dailyLogService.createDailyLog(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. 특정 차량의 전체 운행 기록 조회
    @GetMapping("/vehicle/{vehicleNo}")
    public ResponseEntity<List<VehicleDailyLogResponse>> getLogsByVehicleNo(
            @PathVariable("vehicleNo") String vehicleNo) {
        List<VehicleDailyLogResponse> responses = dailyLogService.getLogsByVehicleNo(vehicleNo);
        return ResponseEntity.ok(responses);
    }

    // 3. 운행 기록 수정
    @PutMapping("/{logId}")
    public ResponseEntity<VehicleDailyLogResponse> updateDailyLog(
            @PathVariable("logId") Long logId,
            @RequestBody VehicleDailyLogUpdateRequest request) {
        VehicleDailyLogResponse response = dailyLogService.updateDailyLog(logId, request);
        return ResponseEntity.ok(response);
    }

    // 4. 운행 기록 삭제
    @DeleteMapping("/{logId}")
    public ResponseEntity<Void> deleteDailyLog(
            @PathVariable("logId") Long logId) {
        dailyLogService.deleteDailyLog(logId);
        return ResponseEntity.ok().build();
    }
}