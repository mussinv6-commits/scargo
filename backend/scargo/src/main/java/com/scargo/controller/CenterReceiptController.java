package com.scargo.controller;

import com.scargo.service.CenterReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// 26.10.23 지역센터 인수 확정 (지선 운행 ARRIVED → RECEIVED)
//  조회: ADMIN(조회만) · CONTROL(도착 전만) · CENTER_OP(소속 센터) / 확정: CENTER_OP(소속 센터)만. 범위 · 상태는 CenterReceiptService
//  ※ @PathVariable · @RequestParam 이름을 직접 적음 (STS 컴파일은 파라미터 이름 정보가 없어서 생략하면 500 오류)
@RestController
@RequestMapping("/api/center-receipts")
@RequiredArgsConstructor
public class CenterReceiptController {

    private final CenterReceiptService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'CENTER_OP')")
    public ResponseEntity<List<Map<String, Object>>> list(
            Authentication authentication, @RequestParam(name = "status", required = false) String status) {
        return ResponseEntity.ok(service.list(authentication, status));
    }

    @PostMapping("/{assignmentId}/receive")
    @PreAuthorize("hasRole('CENTER_OP')")
    public ResponseEntity<Map<String, Object>> receive(Authentication authentication, @PathVariable("assignmentId") Long assignmentId) {
        return ResponseEntity.ok(service.receive(authentication, assignmentId));
    }
}
