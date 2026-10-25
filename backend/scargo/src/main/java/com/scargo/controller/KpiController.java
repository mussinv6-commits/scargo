package com.scargo.controller;

import com.scargo.service.KpiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// 26.10.24 KPI 일별 추이 (읽기 전용). ADMIN · CONTROL 전체 허브, HUB_OP 소속 허브, 그 외 403
//  GET /api/reports/kpi?days=14   (days 1~60)
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'HUB_OP')")
public class KpiController {

    private final KpiService service;

    @GetMapping("/kpi")
    public ResponseEntity<List<Map<String, Object>>> kpi(
            Authentication authentication, @RequestParam(name = "days", required = false) Integer days) {
        return ResponseEntity.ok(service.trend(authentication, days));
    }
}
