package com.scargo.controller;

import com.scargo.dto.HubArrivalCandidateResponse;
import com.scargo.dto.HubArrivalRequest;
import com.scargo.dto.HubHandoverRequest;
import com.scargo.service.HubArrivalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 26.10.23 허브 도착 · 인계
//  조회: ADMIN · CONTROL(전체 허브) · HUB_OP(소속 허브) / 도착 · 인계 확정: ADMIN · HUB_OP(소속 허브)
//  여기서는 역할만 거르고, 허브 범위 · 상태는 HubArrivalService 가 확인한다
//  ※ @RequestParam 이름을 직접 적음 (STS 컴파일은 파라미터 이름 정보가 없어서 생략하면 500 오류)
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HubArrivalController {

    private final HubArrivalService hubArrivalService;

    @GetMapping("/hub-arrivals/candidates")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'HUB_OP')")
    public ResponseEntity<List<HubArrivalCandidateResponse>> candidates(
            Authentication authentication,
            @RequestParam(name = "hubId", required = false) Long hubId,
            @RequestParam(name = "includeHandedOver", defaultValue = "false") boolean includeHandedOver) {
        return ResponseEntity.ok(hubArrivalService.candidates(authentication, hubId, includeHandedOver));
    }

    // 26.10.23 도착 확정 때 고를 입고(IN) 도크 목록
    @GetMapping("/hub-arrivals/docks")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'HUB_OP')")
    public ResponseEntity<List<java.util.Map<String, Object>>> docks(
            Authentication authentication, @RequestParam(name = "hubId", required = false) Long hubId) {
        return ResponseEntity.ok(hubArrivalService.inboundDocks(authentication, hubId));
    }

    @PostMapping("/hub-arrivals")
    @PreAuthorize("hasAnyRole('ADMIN', 'HUB_OP')")
    public ResponseEntity<HubArrivalCandidateResponse> arrive(
            Authentication authentication, @Valid @RequestBody HubArrivalRequest request) {
        return ResponseEntity.ok(hubArrivalService.arrive(authentication, request));
    }

    @PostMapping("/hub-handovers")
    @PreAuthorize("hasAnyRole('ADMIN', 'HUB_OP')")
    public ResponseEntity<HubArrivalCandidateResponse> handover(
            Authentication authentication, @Valid @RequestBody HubHandoverRequest request) {
        return ResponseEntity.ok(hubArrivalService.handover(authentication, request));
    }

    // 26.10.24 하역 완료 — 입고 기록(receiptId)을 COMPLETED 로, 도크 해제
    @PostMapping("/hub-arrivals/{receiptId}/unload")
    @PreAuthorize("hasAnyRole('ADMIN', 'HUB_OP')")
    public ResponseEntity<java.util.Map<String, Object>> unload(
            Authentication authentication, @PathVariable("receiptId") Long receiptId) {
        return ResponseEntity.ok(hubArrivalService.unload(authentication, receiptId));
    }
}
