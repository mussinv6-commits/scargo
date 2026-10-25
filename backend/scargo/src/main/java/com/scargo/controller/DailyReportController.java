package com.scargo.controller;

import com.scargo.service.DailyReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

// 26.10.24 일일 처리량 보고서. 역할: ADMIN · CONTROL(전체) · HUB_OP(소속 허브). 그 외 403
//  GET /api/reports/daily?date=2026-10-24        → JSON (화면)
//  GET /api/reports/daily/xlsx?date=2026-10-24   → Excel 내려받기
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'CONTROL', 'HUB_OP')")
public class DailyReportController {

    private final DailyReportService service;

    @GetMapping("/daily")
    public ResponseEntity<Map<String, Object>> daily(
            Authentication authentication,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.report(authentication, date));
    }

    @GetMapping("/daily/xlsx")
    public ResponseEntity<byte[]> dailyXlsx(
            Authentication authentication,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<String, Object> report = service.report(authentication, date);
        byte[] body = service.toXlsx(report);
        String name = "daily-report-" + report.get("date") + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(body);
    }
}
