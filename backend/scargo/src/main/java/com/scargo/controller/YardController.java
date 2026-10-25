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
// 별도 메서드 권한이 없는 조회 API에 적용
// 항만→허브 업무 이용자와 관리자의 공통 야드 정보 조회 허용
@PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'HUB_OP', 'CONTAINER_DRIVER', 'CORPORATE_APPROVED')")
public class YardController {

    private final YardService yardService;

    // 야드 등록 API
    // POST /api/yards
    // 권한: ADMIN, CONTROL
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL')")
    public ResponseEntity<YardResponse> createYard(
            @Valid @RequestBody YardCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(yardService.createYard(request));
    }

    // 전체 야드 목록 조회 API
    // GET /api/yards
    @GetMapping
    public ResponseEntity<List<YardResponse>> getAllYards() {
        return ResponseEntity.ok(yardService.getAllYards());
    }

    // 야드 선택 목록 조회 API
    // GET /api/yards/options
    @GetMapping("/options")
    public ResponseEntity<List<YardOptionResponse>> getYardOptions() {
        return ResponseEntity.ok(yardService.getYardOptions());
    }

    // 야드 단건 조회 API
    // GET /api/yards/{yardId}
    @GetMapping("/{yardId}")
    public ResponseEntity<YardResponse> getYard(
            @PathVariable("yardId") Long yardId
    ) {
        return ResponseEntity.ok(yardService.getYard(yardId));
    }

    // 야드 유형별 목록 조회 API
    // GET /api/yards/type/{yardType}
    @GetMapping("/type/{yardType}")
    public ResponseEntity<List<YardResponse>> getYardsByType(
            @PathVariable("yardType") String yardType
    ) {
        return ResponseEntity.ok(
                yardService.getYardsByType(yardType)
        );
    }

    // 사용 가능 여부별 야드 목록 조회 API
    // GET /api/yards/availability?isAvailable=true
    @GetMapping("/availability")
    public ResponseEntity<List<YardResponse>> getYardsByAvailability(
            @RequestParam("isAvailable") Boolean isAvailable
    ) {
        return ResponseEntity.ok(
                yardService.getYardsByAvailability(isAvailable)
        );
    }

    // 야드 정보 수정 API
    // PUT /api/yards/{yardId}
    // 실제 상하차 작업·배정 처리는 별도 업무 API에서 수행
    @PutMapping("/{yardId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL')")
    public ResponseEntity<YardResponse> updateYard(
            @PathVariable("yardId") Long yardId,
            @Valid @RequestBody YardUpdateRequest request
    ) {
        return ResponseEntity.ok(
                yardService.updateYard(yardId, request)
        );
    }

    // 야드 삭제 API
    // DELETE /api/yards/{yardId}
    // 삭제 가능 여부는 Service 및 DB 외래 키 정책에 따라 결정
    @DeleteMapping("/{yardId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL')")
    public ResponseEntity<Void> deleteYard(
            @PathVariable("yardId") Long yardId
    ) {
        yardService.deleteYard(yardId);
        return ResponseEntity.noContent().build();
    }
}