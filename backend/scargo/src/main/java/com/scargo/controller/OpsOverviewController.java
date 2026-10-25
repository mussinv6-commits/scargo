package com.scargo.controller;

import com.scargo.service.OpsOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// 26.10.24 운영 현황판 (읽기 전용). ADMIN · CONTROL 전체 허브, HUB_OP 소속 허브, 그 외 403
@RestController
@RequestMapping("/api/ops")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'HUB_OP')")
public class OpsOverviewController {

    private final OpsOverviewService service;

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview(Authentication authentication) {
        return ResponseEntity.ok(service.overview(authentication));
    }
}
