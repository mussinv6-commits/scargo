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

    // 로딩 장소(섹터) 생성 (관리자 및 승인된 기업 회원 허용)
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CORPORATE_APPROVED')") // hasRole('ADMIN') -> hasAnyAuthority로 변경
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
    public ResponseEntity<List<LoadingLocationResponse>> getLocationsByYard(@PathVariable Long yardId) {
        return ResponseEntity.ok(loadingLocationService.getLoadingLocationsByYard(yardId));
    }

    // 드롭다운/셀렉트박스용 경량 옵션 목록 조회
    @GetMapping("/yard/{yardId}/options")
    public ResponseEntity<List<LoadingLocationOptionResponse>> getLocationOptionsByYard(@PathVariable Long yardId) {
        return ResponseEntity.ok(loadingLocationService.getLoadingLocationOptionsByYard(yardId));
    }

    // 특정 야드 내 가용 여부별 필터링 조회
    @GetMapping("/yard/{yardId}/filter")
    public ResponseEntity<List<LoadingLocationResponse>> getLocationsByYardAndAvailability(
            @PathVariable Long yardId,
            @RequestParam Boolean isAvailable) {
        return ResponseEntity.ok(loadingLocationService.getLoadingLocationsByYardAndAvailability(yardId, isAvailable));
    }

    // 단건 로딩 장소 조회
    @GetMapping("/{locationId}")
    public ResponseEntity<LoadingLocationResponse> getLocation(@PathVariable Long locationId) {
        return ResponseEntity.ok(loadingLocationService.getLoadingLocation(locationId));
    }

    // 로딩 장소 정보 수정
    @PatchMapping("/{locationId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CORPORATE_APPROVED')") // hasRole('ADMIN') -> hasAnyAuthority로 변경
    public ResponseEntity<LoadingLocationResponse> updateLocation(
            @PathVariable Long locationId,
            @Valid @RequestBody LoadingLocationUpdateRequest request) {
        return ResponseEntity.ok(loadingLocationService.updateLoadingLocation(locationId, request));
    }

    // 상태 및 가용 여부 빠른 변경 전용
    @PatchMapping("/{locationId}/status")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CORPORATE_APPROVED')") // hasRole('ADMIN') -> hasAnyAuthority로 변경
    public ResponseEntity<LoadingLocationResponse> updateLocationStatus(
            @PathVariable Long locationId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean isAvailable) {
        return ResponseEntity.ok(loadingLocationService.updateLocationStatus(locationId, status, isAvailable));
    }

    // 로딩 장소 삭제 (관리자 전용)
    @DeleteMapping("/{locationId}")
    @PreAuthorize("hasAuthority('ADMIN')") // hasRole('ADMIN') -> hasAuthority로 변경
    public ResponseEntity<Void> deleteLocation(@PathVariable Long locationId) {
        loadingLocationService.deleteLoadingLocation(locationId);
        return ResponseEntity.ok().build();
    }
}