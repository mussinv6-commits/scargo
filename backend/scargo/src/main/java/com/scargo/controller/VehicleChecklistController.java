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

    // GET /api/checklists: 점검표 목록 조회 (실제 기록 범위는 Service에서 제한)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'CORPORATE_APPROVED', 'CONTAINER_DRIVER', 'LINEHAUL_DRIVER')")
    public ResponseEntity<List<VehicleChecklistResponse>> getAllChecklists(Authentication authentication) {
        boolean isAdmin = extractIsAdmin(authentication);
        List<VehicleChecklistResponse> responses = vehicleChecklistService.getAllChecklists(authentication, isAdmin);
        return ResponseEntity.ok(responses);
    }

    // POST /api/checklists: 점검표 등록 (ADMIN·CONTROL·운송사 담당자)
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'CORPORATE_APPROVED')")
    public ResponseEntity<VehicleChecklistResponse> createChecklist(
            @RequestBody VehicleChecklistCreateRequest request,
            Authentication authentication) {
        
        boolean isAdmin = extractIsAdmin(authentication);

        VehicleChecklistResponse response = vehicleChecklistService.createChecklist(request, authentication, isAdmin);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // GET /api/checklists/{id}: 점검표 단건 조회
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'CORPORATE_APPROVED', 'CONTAINER_DRIVER', 'LINEHAUL_DRIVER')")
    public ResponseEntity<VehicleChecklistResponse> getChecklistById(
            @PathVariable("id") Long inspectionId,
            Authentication authentication) {
        
        boolean isAdmin = extractIsAdmin(authentication);

        VehicleChecklistResponse response = vehicleChecklistService.getChecklistById(inspectionId, authentication, isAdmin);
        return ResponseEntity.ok(response);
    }

    // GET /api/checklists/vehicle/{vehicleNo}: 차량별 점검 이력 조회
    @GetMapping("/vehicle/{vehicleNo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'CORPORATE_APPROVED', 'CONTAINER_DRIVER', 'LINEHAUL_DRIVER')")
    public ResponseEntity<List<VehicleChecklistResponse>> getChecklistsByVehicleNo(
            @PathVariable("vehicleNo") String vehicleNo,
            Authentication authentication) {
        
        boolean isAdmin = extractIsAdmin(authentication);

        List<VehicleChecklistResponse> responses = vehicleChecklistService.getChecklistsByVehicleNo(vehicleNo, authentication, isAdmin);
        return ResponseEntity.ok(responses);
    }

    // GET /api/checklists/inspector/{inspectorAccountId}: 본인 작성 이력 조회, ADMIN은 전체 점검자 허용
    @GetMapping("/inspector/{inspectorAccountId}")
    @PreAuthorize("hasRole('ADMIN') or (hasAnyRole('CONTROL', 'CORPORATE_APPROVED', 'CONTAINER_DRIVER', 'LINEHAUL_DRIVER') and #p0 == principal.id)")
    public ResponseEntity<List<VehicleChecklistResponse>> getChecklistsByInspector(
            @PathVariable("inspectorAccountId") Long inspectorAccountId) {
        
        List<VehicleChecklistResponse> responses = vehicleChecklistService.getChecklistsByInspector(inspectorAccountId);
        return ResponseEntity.ok(responses);
    }

    // GET /api/checklists/vehicle/{vehicleNo}/date?date=2026-10-22: 일자별 조회
    @GetMapping("/vehicle/{vehicleNo}/date")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'CORPORATE_APPROVED', 'CONTAINER_DRIVER', 'LINEHAUL_DRIVER')")
    public ResponseEntity<List<VehicleChecklistResponse>> getChecklistsByVehicleAndDate(
            @PathVariable("vehicleNo") String vehicleNo,
            @RequestParam("date") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate inspectionDate,
            Authentication authentication) {
        
        boolean isAdmin = extractIsAdmin(authentication);

        List<VehicleChecklistResponse> responses = vehicleChecklistService.getChecklistsByVehicleAndDate(vehicleNo, inspectionDate, authentication, isAdmin);
        return ResponseEntity.ok(responses);
    }

    // GET /api/checklists/vehicle/{vehicleNo}/period?startDate=2026-10-01&endDate=2026-10-22: 기간별 조회
    @GetMapping("/vehicle/{vehicleNo}/period")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'CORPORATE_APPROVED', 'CONTAINER_DRIVER', 'LINEHAUL_DRIVER')")
    public ResponseEntity<List<VehicleChecklistResponse>> getChecklistsByPeriod(
            @PathVariable("vehicleNo") String vehicleNo,
            @RequestParam("startDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            Authentication authentication) {
        
        boolean isAdmin = extractIsAdmin(authentication);

        List<VehicleChecklistResponse> responses = vehicleChecklistService.getChecklistsByPeriod(vehicleNo, startDate, endDate, authentication, isAdmin);
        return ResponseEntity.ok(responses);
    }

    // PUT /api/checklists/{id}: 점검 내용 수정 (ADMIN·CONTROL·운송사 담당자)
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'CORPORATE_APPROVED')")
    public ResponseEntity<VehicleChecklistResponse> updateChecklist(
            @PathVariable("id") Long inspectionId,
            @RequestBody VehicleChecklistUpdateRequest request,
            Authentication authentication) {
        
        boolean isAdmin = extractIsAdmin(authentication);

        VehicleChecklistResponse response = vehicleChecklistService.updateChecklist(inspectionId, request, authentication, isAdmin);
        return ResponseEntity.ok(response);
    }

    // DELETE /api/checklists/{id}: 점검표 삭제 (ADMIN)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteChecklist(
            @PathVariable("id") Long inspectionId,
            Authentication authentication) {
        
        boolean isAdmin = extractIsAdmin(authentication);

        vehicleChecklistService.deleteChecklist(inspectionId, authentication, isAdmin);
        return ResponseEntity.noContent().build();
    }

    // 실제 ROLE_ADMIN만 판별하며 CONTROL을 관리자로 취급하지 않음
    private boolean extractIsAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(auth -> "ROLE_ADMIN".equals(auth.getAuthority())); // 26.09.30 병합: 세션 필터가 ROLE_ 접두어로 권한을 넣으므로 맞춤
    }
}