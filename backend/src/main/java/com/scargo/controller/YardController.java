package com.scargo.controller;

import com.scargo.dto.YardCreateRequest;
import com.scargo.dto.YardOptionResponse;
import com.scargo.dto.YardResponse;
import com.scargo.dto.YardUpdateRequest;
import com.scargo.service.YardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/yards")
@RequiredArgsConstructor
public class YardController {

    private final YardService yardService;

    // 1. 야드 등록 (관리자 전용)
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')") // hasRole -> hasAuthority로 변경
    public ResponseEntity<YardResponse> createYard(@Valid @RequestBody YardCreateRequest request) {
        YardResponse response = yardService.createYard(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 2. 전체 야드 목록 조회 (인증된 전체 회원 조회 가능)
    @GetMapping
    @PreAuthorize("hasAnyAuthority('GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED', 'ADMIN')") // hasRole -> hasAnyAuthority로 변경
    public ResponseEntity<List<YardResponse>> getAllYards() {
        List<YardResponse> yards = yardService.getAllYards();
        return ResponseEntity.ok(yards);
    }

    // 3. 드롭다운/옵션용 야드 목록 조회 (셀렉트 박스 등)
    @GetMapping("/options")
    public ResponseEntity<List<YardOptionResponse>> getYardOptions() {
        List<YardOptionResponse> options = yardService.getYardOptions();
        return ResponseEntity.ok(options);
    }

    // 4. 단건 야드 조회 (yardId 기준, 인증된 전체 회원 조회 가능)
    @GetMapping("/{yardId}")
    @PreAuthorize("hasAnyAuthority('GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED', 'ADMIN')") // hasRole -> hasAnyAuthority로 변경
    public ResponseEntity<YardResponse> getYard(@PathVariable Long yardId) {
        YardResponse response = yardService.getYard(yardId);
        return ResponseEntity.ok(response);
    }

    // 5. 야드 타입별 조회 (인증된 전체 회원 조회 가능)
    @GetMapping("/type/{yardType}")
    @PreAuthorize("hasAnyAuthority('GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED', 'ADMIN')") // hasRole -> hasAnyAuthority로 변경
    public ResponseEntity<List<YardResponse>> getYardsByType(@PathVariable String yardType) {
        List<YardResponse> yards = yardService.getYardsByType(yardType);
        return ResponseEntity.ok(yards);
    }

    // 6. 사용 가능 여부별 조회 (인증된 전체 회원 조회 가능)
    @GetMapping("/availability")
    @PreAuthorize("hasAnyAuthority('GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED', 'ADMIN')") // hasRole -> hasAnyAuthority로 변경
    public ResponseEntity<List<YardResponse>> getYardsByAvailability(@RequestParam Boolean isAvailable) {
        List<YardResponse> yards = yardService.getYardsByAvailability(isAvailable);
        return ResponseEntity.ok(yards);
    }
    
    // 7. 야드 수정 (관리자 및 승인된 기업 회원 허용)
    @PutMapping("/{yardId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'CORPORATE_APPROVED')") // hasRole -> hasAnyAuthority로 변경
    public ResponseEntity<YardResponse> updateYard(@PathVariable Long yardId, @Valid @RequestBody YardUpdateRequest request) {
        YardResponse response = yardService.updateYard(yardId, request);
        return ResponseEntity.ok(response);
    }

    // 8. 야드 삭제 (관리자 전용)
    @DeleteMapping("/{yardId}")
    @PreAuthorize("hasAuthority('ADMIN')") // hasRole -> hasAuthority로 변경
    public ResponseEntity<Void> deleteYard(@PathVariable Long yardId) {
        yardService.deleteYard(yardId);
        return ResponseEntity.noContent().build();
    }
}