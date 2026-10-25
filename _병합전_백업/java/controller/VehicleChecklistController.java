package com.scargo.controller;

import com.scargo.dto.VehicleChecklistCreateRequest;
import com.scargo.dto.VehicleChecklistResponse;
import com.scargo.dto.VehicleChecklistUpdateRequest;
import com.scargo.service.VehicleChecklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/checklists")
@RequiredArgsConstructor
public class VehicleChecklistController {

    private final VehicleChecklistService vehicleChecklistService;

    // [추가] 전체 점검표 목록 통합 조회 (관리자는 모든 회사, 일반 기업은 소속 회사만 조회)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED', 'GENERAL')")
    public ResponseEntity<List<VehicleChecklistResponse>> getAllChecklists(Authentication authentication) {
        boolean isAdmin = extractIsAdmin(authentication);
        List<VehicleChecklistResponse> responses = vehicleChecklistService.getAllChecklists(authentication, isAdmin);
        return ResponseEntity.ok(responses);
    }

    // 일상점검표 등록
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')")
    public ResponseEntity<VehicleChecklistResponse> createChecklist(
            @RequestBody VehicleChecklistCreateRequest request,
            Authentication authentication) {
        
        boolean isAdmin = extractIsAdmin(authentication);

        VehicleChecklistResponse response = vehicleChecklistService.createChecklist(request, authentication, isAdmin);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 일상점검표 단건 조회
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED', 'GENERAL')")
    public ResponseEntity<VehicleChecklistResponse> getChecklistById(
            @PathVariable("id") Long inspectionId,
            Authentication authentication) {
        
        boolean isAdmin = extractIsAdmin(authentication);

        VehicleChecklistResponse response = vehicleChecklistService.getChecklistById(inspectionId, authentication, isAdmin);
        return ResponseEntity.ok(response);
    }

    // 특정 차량의 전체 점검 이력 조회
    @GetMapping("/vehicle/{vehicleNo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED', 'GENERAL')")
    public ResponseEntity<List<VehicleChecklistResponse>> getChecklistsByVehicleNo(
            @PathVariable("vehicleNo") String vehicleNo,
            Authentication authentication) {
        
        boolean isAdmin = extractIsAdmin(authentication);

        List<VehicleChecklistResponse> responses = vehicleChecklistService.getChecklistsByVehicleNo(vehicleNo, authentication, isAdmin);
        return ResponseEntity.ok(responses);
    }

    // 특정 점검자가 작성한 전체 점검 이력 조회
    @GetMapping("/inspector/{inspectorAccountId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED', 'GENERAL')")
    public ResponseEntity<List<VehicleChecklistResponse>> getChecklistsByInspector(
            @PathVariable("inspectorAccountId") Long inspectorAccountId) {
        
        List<VehicleChecklistResponse> responses = vehicleChecklistService.getChecklistsByInspector(inspectorAccountId);
        return ResponseEntity.ok(responses);
    }

    // 특정 차량의 특정 일자 점검표 조회
    @GetMapping("/vehicle/{vehicleNo}/date")
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')")
    public ResponseEntity<List<VehicleChecklistResponse>> getChecklistsByVehicleAndDate(
            @PathVariable("vehicleNo") String vehicleNo,
            @RequestParam("date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate inspectionDate,
            Authentication authentication) {
        
        boolean isAdmin = extractIsAdmin(authentication);

        List<VehicleChecklistResponse> responses = vehicleChecklistService.getChecklistsByVehicleAndDate(vehicleNo, inspectionDate, authentication, isAdmin);
        return ResponseEntity.ok(responses);
    }

    // 특정 차량의 특정 기간 점검표 조회
    @GetMapping("/vehicle/{vehicleNo}/period")
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED', 'GENERAL')")
    public ResponseEntity<List<VehicleChecklistResponse>> getChecklistsByPeriod(
            @PathVariable("vehicleNo") String vehicleNo,
            @RequestParam("startDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            Authentication authentication) {
        
        boolean isAdmin = extractIsAdmin(authentication);

        List<VehicleChecklistResponse> responses = vehicleChecklistService.getChecklistsByPeriod(vehicleNo, startDate, endDate, authentication, isAdmin);
        return ResponseEntity.ok(responses);
    }

    // 일상점검표 수정
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')")
    public ResponseEntity<VehicleChecklistResponse> updateChecklist(
            @PathVariable("id") Long inspectionId,
            @RequestBody VehicleChecklistUpdateRequest request,
            Authentication authentication) {
        
        boolean isAdmin = extractIsAdmin(authentication);

        VehicleChecklistResponse response = vehicleChecklistService.updateChecklist(inspectionId, request, authentication, isAdmin);
        return ResponseEntity.ok(response);
    }

    // 일상점검표 삭제
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteChecklist(
            @PathVariable("id") Long inspectionId,
            Authentication authentication) {
        
        boolean isAdmin = extractIsAdmin(authentication);

        vehicleChecklistService.deleteChecklist(inspectionId, authentication, isAdmin);
        return ResponseEntity.noContent().build();
    }

    // 인증 객체에서 관리자 여부 추출
    private boolean extractIsAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN")); // 26.09.30 병합: 세션 필터가 ROLE_ 접두어로 권한을 넣으므로 맞춤
    }
}