package com.scargo.controller;

import com.scargo.service.DriverTransportOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// 26.10.23 컨테이너 기사 운송 오더: 내 오더 조회 · 운송 출발
//  ※ @PathVariable 이름을 직접 적음 (STS 컴파일은 파라미터 이름 정보가 없어서 생략하면 500 오류)
@RestController
@RequestMapping("/api/transport-orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CONTAINER_DRIVER')")
public class DriverTransportOrderController {

    private final DriverTransportOrderService service;

    @GetMapping("/my")
    public ResponseEntity<List<Map<String, Object>>> myOrders(Authentication authentication) {
        return ResponseEntity.ok(service.myOrders(authentication));
    }

    @PostMapping("/{orderId}/depart")
    public ResponseEntity<Map<String, Object>> depart(Authentication authentication, @PathVariable("orderId") Long orderId) {
        return ResponseEntity.ok(service.depart(authentication, orderId));
    }
}
