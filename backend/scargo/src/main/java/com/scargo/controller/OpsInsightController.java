package com.scargo.controller;

import com.scargo.service.AlertService;
import com.scargo.service.CompanyScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// 26.10.24 경보 규칙 + 업체별 실적 (읽기 전용)
//  GET /api/ops/alerts                           ADMIN · CONTROL 전체, HUB_OP 소속 허브
//  GET /api/reports/company-scorecard?days=30    ADMIN · CONTROL
//  GET /api/reports/company-scorecard?from=2026-10-01&to=2026-10-29  (26.10.29 기간 지정)
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
    public ResponseEntity<List<Map<String, Object>>> scorecard(@RequestParam(name = "days", required = false) Integer days,
                                                               @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                               @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        // 26.10.29 시작일 · 종료일(from/to, yyyy-MM-dd) 추가. 없으면 예전처럼 최근 days 일
        return ResponseEntity.ok(scoreService.scorecard(days, from, to));
    }
}
