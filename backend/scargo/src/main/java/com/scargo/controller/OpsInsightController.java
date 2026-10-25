package com.scargo.controller;

import com.scargo.service.AlertService;
import com.scargo.service.CompanyScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

// 26.10.24 경보 규칙 + 업체별 실적 (읽기 전용)
//  GET /api/ops/alerts                           ADMIN · CONTROL 전체, HUB_OP 소속 허브
//  GET /api/reports/company-scorecard?days=30    ADMIN · CONTROL
@RestController
@RequiredArgsConstructor
public class OpsInsightController {

    private final AlertService alertService;
    private final CompanyScoreService scoreService;

    @GetMapping("/api/ops/alerts")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'HUB_OP')")
    public ResponseEntity<Map<String, Object>> alerts(Authentication authentication) {
        return ResponseEntity.ok(alertService.alerts(authentication));
    }

    @GetMapping("/api/reports/company-scorecard")
    @PreAuthorize("hasAnyRole('ADMIN', 'CONTROL')")
    public ResponseEntity<List<Map<String, Object>>> scorecard(@RequestParam(name = "days", required = false) Integer days) {
        return ResponseEntity.ok(scoreService.scorecard(days));
    }
}
