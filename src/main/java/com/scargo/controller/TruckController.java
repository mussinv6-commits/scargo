package com.scargo.controller;

import com.scargo.dto.TruckCreateRequest;
import com.scargo.dto.TruckOptionResponse;
import com.scargo.dto.TruckResponse;
import com.scargo.dto.TruckUpdateRequest;
import com.scargo.service.TruckService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trucks")
@RequiredArgsConstructor
public class TruckController {

    private final TruckService truckService;

    // 차량 등록
    // 일반 회원 및 승인된 기업 회원/관리자 누구나 입차 시 등록 필요할 수 있음
    @PostMapping
    public ResponseEntity<TruckResponse> createTruck(@Valid @RequestBody TruckCreateRequest request) {
        TruckResponse response = truckService.createTruck(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 전체 차량 목록 조회 (관리자 및 승인된 기업 회원 허용)
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CORPORATE_APPROVED')") // hasRole -> hasAnyAuthority로 변경
    public ResponseEntity<List<TruckResponse>> getAllTrucks() {
        List<TruckResponse> responses = truckService.getAllTrucks();
        return ResponseEntity.ok(responses);
    }

    // 특정 차량 단건 상세 조회 (관리자 및 승인된 기업 회원 허용)
    @GetMapping("/{vehicleNo}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CORPORATE_APPROVED')") // hasRole -> hasAnyAuthority로 변경
    public ResponseEntity<TruckResponse> getTruck(@PathVariable String vehicleNo) {
        TruckResponse response = truckService.getTruck(vehicleNo);
        return ResponseEntity.ok(response);
    }

    // 업체별 드롭다운/선택용 경량 차량 옵션 목록 조회
    @GetMapping("/options")
    public ResponseEntity<List<TruckOptionResponse>> getTruckOptions(@RequestParam Long companyId) {
        List<TruckOptionResponse> responses = truckService.getTruckOptions(companyId);
        return ResponseEntity.ok(responses);
    }

    // 차량 전체/부분 정보 수정 (관리자 및 승인된 기업 회원 허용)
    @PutMapping("/{vehicleNo}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CORPORATE_APPROVED')") // hasRole -> hasAnyAuthority로 변경
    public ResponseEntity<TruckResponse> updateTruck(
            @PathVariable String vehicleNo,
            @Valid @RequestBody TruckUpdateRequest request) {
        TruckResponse response = truckService.updateTruck(vehicleNo, request);
        return ResponseEntity.ok(response);
    }

    // 차량 상태만 단독 변경 (OUTSIDE, INSIDE, IN_TRANSIT 등)
    @PatchMapping("/{vehicleNo}/status")
    public ResponseEntity<TruckResponse> updateTruckStatus(
            @PathVariable String vehicleNo,
            @RequestParam String status) {
        TruckResponse response = truckService.updateTruckStatus(vehicleNo, status);
        return ResponseEntity.ok(response);
    }

    // 차량 정보 삭제 (관리자 전용)
    @DeleteMapping("/{vehicleNo}")
    @PreAuthorize("hasAuthority('ADMIN')") // hasRole -> hasAuthority로 변경
    public ResponseEntity<Void> deleteTruck(@PathVariable String vehicleNo) {
        truckService.deleteTruck(vehicleNo);
        return ResponseEntity.noContent().build();
    }
}