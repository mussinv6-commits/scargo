package com.scargo.controller;

import com.scargo.service.GateReservationService;
import com.scargo.service.GateReservationService.CreateRequest;
import com.scargo.service.GateReservationService.UpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// 26.10.24 게이트 사전 예약. 역할: ADMIN · CONTROL · CORPORATE_APPROVED · CONTAINER_DRIVER (범위는 GateReservationService 가 확인)
//  ※ @PathVariable · @RequestParam 이름을 직접 적음 (STS 컴파일은 파라미터 이름 정보가 없어서 생략하면 500 오류)
@RestController
@RequestMapping("/api/gate-reservations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'CORPORATE_APPROVED', 'CONTAINER_DRIVER')")
public class GateReservationController {

    private final GateReservationService service;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            Authentication authentication,
            @RequestParam(name = "date", required = false) String date,
            @RequestParam(name = "status", required = false) String status) {
        return ResponseEntity.ok(service.list(authentication, date, status));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(Authentication authentication, @RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(authentication, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(
            Authentication authentication, @PathVariable("id") Long id, @RequestBody UpdateRequest request) {
        return ResponseEntity.ok(service.update(authentication, id, request));
    }
}
