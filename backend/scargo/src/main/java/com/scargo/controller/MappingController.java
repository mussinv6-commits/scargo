package com.scargo.controller;

import com.scargo.dto.ContainerOptionResponse;
import com.scargo.dto.ContainerResponse; // 26.10.01 병합
import com.scargo.dto.MappingCreateRequest;
import com.scargo.dto.MappingUpdateRequest; // 26.10.01 병합
import com.scargo.dto.TruckOptionResponse;
import com.scargo.service.MappingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 26.10.22 수정: 차량-컨테이너 매핑 권한
//  ADMIN · CONTROL = 전체 업체 조회 · 생성 · 수정 · 해제, CORPORATE_APPROVED = 자기 업체만, 그 외 역할 403, 비로그인 401
//  업체 범위 · 대상 자원 검증은 MappingService 에서 한 번 더 확인
@RestController
@RequestMapping("/api/mappings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'CORPORATE_APPROVED')")
public class MappingController {

    private final MappingService mappingService;

    //배정 가능한 컨테이너 목록 조회
    @GetMapping("/available-containers")
    public ResponseEntity<List<ContainerOptionResponse>> getAvailableContainers(Authentication authentication) {
        return ResponseEntity.ok(mappingService.getAvailableContainers(authentication));
    }

    // 소속 업체의 차량 목록 조회 (ADMIN · CONTROL 은 전체 업체)
    @GetMapping("/my-trucks")
    public ResponseEntity<List<TruckOptionResponse>> getCompanyTrucks(Authentication authentication) {
        return ResponseEntity.ok(mappingService.getCompanyTrucks(authentication));
    }

    // 차량-컨테이너 매핑(배정) 생성
    @PostMapping
    public ResponseEntity<Void> createMapping(
            Authentication authentication,
            @Valid @RequestBody MappingCreateRequest request // @Valid 추가로 입력값 검증 자동화
    ) {
        mappingService.createMapping(authentication, request);
        // 생성 리소스 성공 응답은 200 OK보다 201 Created 사용이 RESTful 표준에 적합
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // 26.10.01 병합: 현재 매핑 목록 조회 (사업자 매핑 화면 "현재 매핑" 표)
    @GetMapping("/active")
    public ResponseEntity<List<ContainerResponse>> getActiveMappings(Authentication authentication) {
        return ResponseEntity.ok(mappingService.getActiveMappings(authentication));
    }

    // 26.10.01 병합: 매핑 수정 - 컨테이너의 배정 차량 변경 (body: {"vehicleNo": "..."})
    @PutMapping("/{containerNo}")
    public ResponseEntity<Void> changeMapping(
            Authentication authentication,
            @PathVariable("containerNo") String containerNo,
            @Valid @RequestBody MappingUpdateRequest request
    ) {
        mappingService.changeMapping(authentication, containerNo, request);
        return ResponseEntity.ok().build();
    }

    // 차량-컨테이너 매핑(배정) 해제
    @DeleteMapping("/{containerNo}")
    public ResponseEntity<Void> cancelMapping(
            Authentication authentication,
            @PathVariable("containerNo") String containerNo
    ) {
        mappingService.cancelMapping(authentication, containerNo);
        // 삭제/해제 성공 시 내용 없음 응답(204 No Content) 적용
        return ResponseEntity.noContent().build();
    }
}
