package com.scargo.controller;

import com.scargo.dto.GateCreateRequest;
import com.scargo.dto.GateResponse;
import com.scargo.dto.GateUpdateRequest;
import com.scargo.service.GateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/gates")
@RequiredArgsConstructor
public class GateController {

    private final GateService gateService;

    // 1. 게이트 등록 API
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Long> createGate(@RequestBody @Valid GateCreateRequest request) {
        Long gateId = gateService.createGate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(gateId);
    }

    // 2. 전체 게이트 목록 조회 API
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<GateResponse>> getAllGates() {
        List<GateResponse> gates = gateService.getAllGates();
        return ResponseEntity.ok(gates);
    }

    // 2-1. 활성화된 게이트 목록 조회 API
    @GetMapping("/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<GateResponse>> getActiveGates() {
        List<GateResponse> gates = gateService.getActiveGates();
        return ResponseEntity.ok(gates);
    }

    // 2-2. 게이트 유형별(IN/OUT) 목록 조회 API
    @GetMapping("/type/{gateType}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<GateResponse>> getGatesByType(@PathVariable("gateType") String gateType) {
        List<GateResponse> gates = gateService.getGatesByType(gateType);
        return ResponseEntity.ok(gates);
    }

    // 3. 특정 게이트 단건 조회 API
    @GetMapping("/{gateId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GateResponse> getGateById(@PathVariable("gateId") Long gateId) {
        GateResponse gate = gateService.getGateById(gateId);
        return ResponseEntity.ok(gate);
    }

    // 4. 게이트 정보 수정 API
    @PatchMapping("/{gateId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateGate(
            @PathVariable("gateId") Long gateId,
            @RequestBody @Valid GateUpdateRequest request) {
        gateService.updateGate(gateId, request);
        return ResponseEntity.noContent().build();
    }

    // 5. 게이트 소프트 삭제(비활성화) API
    @DeleteMapping("/{gateId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteGate(@PathVariable("gateId") Long gateId) {
        gateService.deleteGate(gateId);
        return ResponseEntity.noContent().build();
    }
}