package com.scargo.controller;

import com.scargo.dto.GateLogCreateRequest;
import com.scargo.dto.GateLogResponse;
import com.scargo.dto.GateLogUpdateRequest;
import com.scargo.service.GateLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/gate-logs")
@RequiredArgsConstructor
public class GateLogController {

    private final GateLogService gateLogService;

    // 1. 게이트 통과 이력 생성 (관리자 및 승인된 기업 회원 권한 허용)
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CORPORATE_APPROVED')") // hasRole -> hasAnyAuthority 변경
    public ResponseEntity<GateLogResponse> createGateLog(@Valid @RequestBody GateLogCreateRequest request) {
        GateLogResponse response = gateLogService.createGateLog(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. 게이트 통과 이력 단건 조회
    @GetMapping("/{gateLogId}")
    public ResponseEntity<GateLogResponse> getGateLog(@PathVariable("gateLogId") Long gateLogId) {
        GateLogResponse response = gateLogService.getGateLog(gateLogId);
        return ResponseEntity.ok(response);
    }

    // 3. 전체 게이트 통과 이력 조회 
    @GetMapping
    public ResponseEntity<Page<GateLogResponse>> getAllGateLogs(
            @PageableDefault(size = 10, sort = "passAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<GateLogResponse> response = gateLogService.getAllGateLogs(pageable);
        return ResponseEntity.ok(response);
    }

    // 4. 차량 번호 기반 이력 검색
    @GetMapping("/search/vehicle")
    public ResponseEntity<Page<GateLogResponse>> getGateLogsByVehicleNo(
            @RequestParam("actualVehicleNo") String actualVehicleNo,
            @PageableDefault(size = 10, sort = "passAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<GateLogResponse> response = gateLogService.getGateLogsByActualVehicleNo(actualVehicleNo, pageable);
        return ResponseEntity.ok(response);
    }

    // 5. 게이트 타입(IN/OUT)별 이력 검색
    @GetMapping("/search/type")
    public ResponseEntity<Page<GateLogResponse>> getGateLogsByGateType(
            @RequestParam("gateType") String gateType,
            @PageableDefault(size = 10, sort = "passAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<GateLogResponse> response = gateLogService.getGateLogsByGateType(gateType, pageable);
        return ResponseEntity.ok(response);
    }

    // 6. 기간별 이력 검색
    @GetMapping("/search/period")
    public ResponseEntity<Page<GateLogResponse>> getGateLogsBetween(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end,
            @PageableDefault(size = 10, sort = "passAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<GateLogResponse> response = gateLogService.getGateLogsBetween(start, end, pageable);
        return ResponseEntity.ok(response);
    }

    // 7. 게이트 통과 이력 수동 수정 (관리자 및 승인된 기업 회원 권한 허용)
    @PatchMapping("/{gateLogId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CORPORATE_APPROVED')") // hasRole -> hasAnyAuthority 변경
    public ResponseEntity<GateLogResponse> updateGateLog(
            @PathVariable("gateLogId") Long gateLogId,
            @Valid @RequestBody GateLogUpdateRequest request) {
        GateLogResponse response = gateLogService.updateGateLog(gateLogId, request);
        return ResponseEntity.ok(response);
    }

    // 8. 게이트 통과 이력 삭제 (관리자 권한만)
    @DeleteMapping("/{gateLogId}")
    @PreAuthorize("hasAuthority('ADMIN')") // hasRole -> hasAuthority 변경
    public ResponseEntity<Void> deleteGateLog(@PathVariable("gateLogId") Long gateLogId) {
        gateLogService.deleteGateLog(gateLogId);
        return ResponseEntity.noContent().build();
    }
}