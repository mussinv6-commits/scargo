package com.scargo.controller;

import com.scargo.dto.LoadingLocationCreateRequest;
import com.scargo.dto.LoadingLocationOptionResponse;
import com.scargo.dto.LoadingLocationResponse;
import com.scargo.dto.LoadingLocationUpdateRequest;
import com.scargo.service.LoadingLocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/loading-locations")
@RequiredArgsConstructor
public class LoadingLocationController {

    private final LoadingLocationService loadingLocationService;

    // 로딩 장소(섹터) 생성 
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')") // 26.09.30 병합: 사업자 허용(백엔드.zip)
    public ResponseEntity<LoadingLocationResponse> createLocation(@Valid @RequestBody LoadingLocationCreateRequest request) {
        return ResponseEntity.ok(loadingLocationService.createLoadingLocation(request));
    }

    // 전체 로딩 장소 목록 조회
    @GetMapping
    public ResponseEntity<List<LoadingLocationResponse>> getAllLocations() {
        return ResponseEntity.ok(loadingLocationService.getAllLoadingLocations());
    }

    // 특정 야드의 로딩 장소 목록 조회
    @GetMapping("/yard/{yardId}")  
    public ResponseEntity<List<LoadingLocationResponse>> getLocationsByYard(@PathVariable("yardId") Long yardId) {
        return ResponseEntity.ok(loadingLocationService.getLoadingLocationsByYard(yardId));
    }

    // 드롭다운/셀렉트박스용 경량 옵션 목록 조회 (예: /api/loading-locations/yard/1/options)
    @GetMapping("/yard/{yardId}/options")
    public ResponseEntity<List<LoadingLocationOptionResponse>> getLocationOptionsByYard(@PathVariable("yardId") Long yardId) {
        return ResponseEntity.ok(loadingLocationService.getLoadingLocationOptionsByYard(yardId));
    }

    // 특정 야드 내 가용 여부별 필터링 조회 (예: /api/loading-locations/yard/1/filter?isAvailable=true)
    @GetMapping("/yard/{yardId}/filter")
    public ResponseEntity<List<LoadingLocationResponse>> getLocationsByYardAndAvailability(
            @PathVariable("yardId") Long yardId,
            @RequestParam("isAvailable") Boolean isAvailable) {
        return ResponseEntity.ok(loadingLocationService.getLoadingLocationsByYardAndAvailability(yardId, isAvailable));
    }

    // 단건 로딩 장소 조회
    @GetMapping("/{locationId}")
    public ResponseEntity<LoadingLocationResponse> getLocation(@PathVariable("locationId") Long locationId) {
        return ResponseEntity.ok(loadingLocationService.getLoadingLocation(locationId));
    }

    // 로딩 장소 정보 수정 
    @PatchMapping("/{locationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')") // 26.09.30 병합: 사업자 허용(백엔드.zip)
    public ResponseEntity<LoadingLocationResponse> updateLocation(
            @PathVariable("locationId") Long locationId,
            @Valid @RequestBody LoadingLocationUpdateRequest request) {
        return ResponseEntity.ok(loadingLocationService.updateLoadingLocation(locationId, request));
    }

    // 상태 빠른 변경 전용 (26.10.01 병합(태수님): status만 전달, isAvailable은 status 기준으로 자동 계산)
    @PatchMapping("/{locationId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'CORPORATE_APPROVED')") // 26.09.30 병합: 사업자 허용(백엔드.zip)
    public ResponseEntity<LoadingLocationResponse> updateLocationStatus(
            @PathVariable("locationId") Long locationId,
            @RequestParam(value = "status", required = false) String status) {
        return ResponseEntity.ok(loadingLocationService.updateLocationStatus(locationId, status));
    }

    // 로딩 장소 삭제 
    @DeleteMapping("/{locationId}")
    @PreAuthorize("hasRole('ADMIN')")  // 관리자만
    public ResponseEntity<Void> deleteLocation(@PathVariable("locationId") Long locationId) {
        loadingLocationService.deleteLoadingLocation(locationId);
        return ResponseEntity.ok().build();
    }
}