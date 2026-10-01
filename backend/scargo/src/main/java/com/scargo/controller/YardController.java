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

    // 야드 등록
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<YardResponse> createYard(@Valid @RequestBody YardCreateRequest request) {
        YardResponse response = yardService.createYard(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 전체 야드 목록 조회
    @GetMapping
    @PreAuthorize("hasAnyRole('GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED', 'ADMIN')") // 26.09.30 병합: 로그인 회원 전체 조회 허용(백엔드.zip)
    public ResponseEntity<List<YardResponse>> getAllYards() {
        List<YardResponse> yards = yardService.getAllYards();
        return ResponseEntity.ok(yards);
    }

    // 드롭다운/옵션용 야드 목록 조회 (셀렉트 박스 등)
    @GetMapping("/options")
    public ResponseEntity<List<YardOptionResponse>> getYardOptions() {
        List<YardOptionResponse> options = yardService.getYardOptions();
        return ResponseEntity.ok(options);
    }

    // 단건 야드 조회 (yardId 기준)
    @GetMapping("/{yardId}")
    @PreAuthorize("hasAnyRole('GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED', 'ADMIN')") // 26.09.30 병합: 로그인 회원 전체 조회 허용(백엔드.zip)
    public ResponseEntity<YardResponse> getYard(@PathVariable("yardId") Long yardId) {
        YardResponse response = yardService.getYard(yardId);
        return ResponseEntity.ok(response);
    }

    // 야드 타입별 조회
    @GetMapping("/type/{yardType}")
    @PreAuthorize("hasAnyRole('GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED', 'ADMIN')") // 26.09.30 병합: 로그인 회원 전체 조회 허용(백엔드.zip)
    public ResponseEntity<List<YardResponse>> getYardsByType(@PathVariable("yardType") String yardType) {
        List<YardResponse> yards = yardService.getYardsByType(yardType);
        return ResponseEntity.ok(yards);
    }

    // 사용 가능 여부별 조회 (예: /api/yards/availability?isAvailable=true)
    @GetMapping("/availability")
    @PreAuthorize("hasAnyRole('GENERAL', 'CORPORATE_PENDING', 'CORPORATE_APPROVED', 'ADMIN')") // 26.09.30 병합: 로그인 회원 전체 조회 허용(백엔드.zip)
    public ResponseEntity<List<YardResponse>> getYardsByAvailability(@RequestParam("isAvailable") Boolean isAvailable) {
        List<YardResponse> yards = yardService.getYardsByAvailability(isAvailable);
        return ResponseEntity.ok(yards);
    }
    
    // 야드 수정
    @PutMapping("/{yardId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<YardResponse> updateYard(@PathVariable("yardId") Long yardId, @Valid @RequestBody YardUpdateRequest request) {
        YardResponse response = yardService.updateYard(yardId, request);
        return ResponseEntity.ok(response);
    }

    // 야드 삭제
    @DeleteMapping("/{yardId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteYard(@PathVariable("yardId") Long yardId) {
        yardService.deleteYard(yardId);
        return ResponseEntity.noContent().build();
    }
}