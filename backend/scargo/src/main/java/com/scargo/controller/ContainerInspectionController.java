package com.scargo.controller;

import com.scargo.dto.*;
import com.scargo.service.ContainerInspectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 26.10.23 컨테이너 점검표 (7-point)
//  역할: ADMIN · CONTROL(조회만) · CORPORATE_APPROVED · CONTAINER_DRIVER · HUB_OP. CENTER_OP 등은 403, 비로그인 401
//  업체 · 허브 범위, 업무 단계, 기록 상태는 ContainerInspectionService · ContainerInspectionAccessPolicy 가 확인한다
//  ※ @PathVariable · @RequestParam 이름을 직접 적음 (STS 컴파일은 파라미터 이름 정보가 없어서 생략하면 500 오류)
@RestController
@RequestMapping("/api/container-inspections")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'CORPORATE_APPROVED', 'CONTAINER_DRIVER', 'HUB_OP')")
public class ContainerInspectionController {

    private final ContainerInspectionService service;

    @PostMapping
    public ResponseEntity<ContainerInspectionResponse> create(
            Authentication authentication, @Valid @RequestBody ContainerInspectionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(authentication, request));
    }

    // 내 범위의 점검 기록 (필터: containerNo · orderId · status = DRAFT/FINALIZED/CANCELLED)
    @GetMapping
    public ResponseEntity<List<ContainerInspectionResponse>> list(
            Authentication authentication,
            @RequestParam(name = "containerNo", required = false) String containerNo,
            @RequestParam(name = "orderId", required = false) Long orderId,
            @RequestParam(name = "status", required = false) String status) {
        return ResponseEntity.ok(service.list(authentication, containerNo, orderId, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContainerInspectionResponse> get(Authentication authentication, @PathVariable("id") Long id) {
        return ResponseEntity.ok(service.get(authentication, id));
    }

    // 작성 중(DRAFT) 기록의 items · memo 수정
    @PutMapping("/{id}")
    public ResponseEntity<ContainerInspectionResponse> update(
            Authentication authentication, @PathVariable("id") Long id,
            @Valid @RequestBody ContainerInspectionUpdateRequest request) {
        return ResponseEntity.ok(service.update(authentication, id, request));
    }

    @PostMapping("/{id}/finalize")
    public ResponseEntity<ContainerInspectionResponse> finalizeInspection(Authentication authentication, @PathVariable("id") Long id) {
        return ResponseEntity.ok(service.finalizeInspection(authentication, id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ContainerInspectionResponse> cancel(
            Authentication authentication, @PathVariable("id") Long id,
            @Valid @RequestBody ContainerInspectionCancelRequest request) {
        return ResponseEntity.ok(service.cancel(authentication, id, request));
    }

    // 확정된 기록(체인 마지막)의 재점검 → 새 DRAFT
    @PostMapping("/{id}/reinspect")
    public ResponseEntity<ContainerInspectionResponse> reinspect(
            Authentication authentication, @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) ContainerInspectionReinspectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.reinspect(authentication, id,
                request == null ? new ContainerInspectionReinspectRequest() : request));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<ContainerInspectionAuditResponse>> history(Authentication authentication, @PathVariable("id") Long id) {
        return ResponseEntity.ok(service.history(authentication, id));
    }
}
