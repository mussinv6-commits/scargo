package com.scargo.controller;

import com.scargo.dto.LoadingRecordCreateRequest;
import com.scargo.dto.LoadingRecordResponse;
import com.scargo.dto.LoadingRecordSearchCondition;
import com.scargo.dto.LoadingRecordUpdateRequest;
import com.scargo.entity.LoadingRecord.LoadingStatus;
import com.scargo.service.LoadingRecordService;
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
@RequestMapping("/api/loading-records")
@RequiredArgsConstructor
public class LoadingRecordController {

    private final LoadingRecordService loadingRecordService;

    // 1. 적재 기록 등록 (승인된 기업 회원 또는 관리자)
    @PostMapping
    @PreAuthorize("hasAnyRole('CORPORATE_APPROVED', 'ADMIN')")
    public ResponseEntity<LoadingRecordResponse> createLoadingRecord(
            @Valid @RequestBody LoadingRecordCreateRequest request) {
        LoadingRecordResponse response = loadingRecordService.createLoadingRecord(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. 적재 기록 단건 조회 (모든 인증된 회원)
    @GetMapping("/{recordId}")
    @PreAuthorize("hasAnyRole('GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED', 'ADMIN')")
    public ResponseEntity<LoadingRecordResponse> getLoadingRecord(
            @PathVariable("recordId") Long recordId) {
        LoadingRecordResponse response = loadingRecordService.getLoadingRecord(recordId);
        return ResponseEntity.ok(response);
    }

    // 3. 적재 기록 전체 목록 조회 (모든 인증된 회원)
    @GetMapping
    @PreAuthorize("hasAnyRole('GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED', 'ADMIN')")
    public ResponseEntity<Page<LoadingRecordResponse>> getAllLoadingRecords(
            @PageableDefault(size = 20, sort = "recordId", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<LoadingRecordResponse> response = loadingRecordService.getAllLoadingRecords(pageable);
        return ResponseEntity.ok(response);
    }

    // 4. 특정 차량의 적재 기록 목록 조회 (모든 인증된 회원)
    @GetMapping("/truck/{vehicleNo}")
    @PreAuthorize("hasAnyRole('GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED', 'ADMIN')")
    public ResponseEntity<Page<LoadingRecordResponse>> getLoadingRecordsByVehicleNo(
            @PathVariable("vehicleNo") String vehicleNo,
            @PageableDefault(size = 20, sort = "recordId", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<LoadingRecordResponse> response = loadingRecordService.getLoadingRecordsByVehicleNo(vehicleNo, pageable);
        return ResponseEntity.ok(response);
    }

    // 5. 특정 컨테이너의 적재 기록 목록 조회 (모든 인증된 회원)
    @GetMapping("/container/{containerNo}")
    @PreAuthorize("hasAnyRole('GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED', 'ADMIN')")
    public ResponseEntity<Page<LoadingRecordResponse>> getLoadingRecordsByContainerNo(
            @PathVariable("containerNo") String containerNo,
            @PageableDefault(size = 20, sort = "recordId", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<LoadingRecordResponse> response = loadingRecordService.getLoadingRecordsByContainerNo(containerNo, pageable);
        return ResponseEntity.ok(response);
    }

    // 6. 특정 적재 장소의 적재 기록 목록 조회 (모든 인증된 회원)
    @GetMapping("/location/{locationId}")
    @PreAuthorize("hasAnyRole('GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED', 'ADMIN')")
    public ResponseEntity<Page<LoadingRecordResponse>> getLoadingRecordsByLocationId(
            @PathVariable("locationId") Long locationId,
            @PageableDefault(size = 20, sort = "recordId", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<LoadingRecordResponse> response = loadingRecordService.getLoadingRecordsByLocationId(locationId, pageable);
        return ResponseEntity.ok(response);
    }

    // 7. 기간별 적재 기록 조회 (모든 인증된 회원)
    // 예: GET /api/loading-records/period?start=2026-09-01T00:00:00%2B09:00&end=2026-09-21T23:59:59%2B09:00
    @GetMapping("/period")
    @PreAuthorize("hasAnyRole('GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED', 'ADMIN')")
    public ResponseEntity<Page<LoadingRecordResponse>> getLoadingRecordsByPeriod(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end,
            @PageableDefault(size = 20, sort = "loadedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<LoadingRecordResponse> response = loadingRecordService.getLoadingRecordsByPeriod(start, end, pageable);
        return ResponseEntity.ok(response);
    }

    // 8. 다중 조건 동적 검색 (QueryDSL 활용 - 모든 인증된 회원)
    // 예: GET /api/loading-records/search?vehicleNo=12가3456&status=IN_PROGRESS&page=0&size=20
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED', 'ADMIN')")
    public ResponseEntity<Page<LoadingRecordResponse>> searchLoadingRecords(
            @ModelAttribute LoadingRecordSearchCondition condition,
            @PageableDefault(size = 20, sort = "recordId", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<LoadingRecordResponse> response = loadingRecordService.searchLoadingRecords(condition, pageable);
        return ResponseEntity.ok(response);
    }

    // 9. 작업 상태(Status) 변경 (승인된 기업 회원 또는 관리자)
    // 예: PATCH /api/loading-records/1/status?status=COMPLETED
    @PatchMapping("/{recordId}/status")
    @PreAuthorize("hasAnyRole('CORPORATE_APPROVED', 'ADMIN')")
    public ResponseEntity<LoadingRecordResponse> updateStatus(
            @PathVariable("recordId") Long recordId,
            @RequestParam("status") LoadingStatus status) {
        LoadingRecordResponse response = loadingRecordService.updateStatus(recordId, status);
        return ResponseEntity.ok(response);
    }

    // 10. 적재 기록 전체 정보 수정 (승인된 기업 회원 또는 관리자)
    @PutMapping("/{recordId}")
    @PreAuthorize("hasAnyRole('CORPORATE_APPROVED', 'ADMIN')")
    public ResponseEntity<LoadingRecordResponse> updateLoadingRecord(
            @PathVariable("recordId") Long recordId,
            @Valid @RequestBody LoadingRecordUpdateRequest request) {
        LoadingRecordResponse response = loadingRecordService.updateLoadingRecord(recordId, request);
        return ResponseEntity.ok(response);
    }

    // 11. 적재 기록 삭제 (관리자 전용)
    @DeleteMapping("/{recordId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteLoadingRecord(@PathVariable("recordId") Long recordId) {
        loadingRecordService.deleteLoadingRecord(recordId);
        return ResponseEntity.noContent().build();
    }
}