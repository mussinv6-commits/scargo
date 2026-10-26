package com.scargo.scheduler;

import com.scargo.ops.service.ReturnDueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 공컨 반납 기한: v10 알림 + v11 예외 동기화. 10분마다 (scargo.return-due.interval-ms) */
@Slf4j
@Component
@RestController
@RequiredArgsConstructor
public class ReturnDueScheduler {
    private final ReturnDueService service;

    @Scheduled(initialDelayString = "${scargo.return-due.initial-delay-ms:30000}", fixedDelayString = "${scargo.return-due.interval-ms:600000}")
    public void scheduled() {
        try { Map<String, Integer> r = service.run(); if (r.values().stream().mapToInt(Integer::intValue).sum() - r.get("dueSoonHours") > 0) log.info("반납 기한 처리 {}", r); }
        catch (Exception e) { log.warn("반납 기한 처리 실패: {}", e.getMessage()); }
    }

    /** 수동 실행 (총관리자·시연·테스트용) */
    @PostMapping("/api/web/jobs/return-due")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Integer> runNow() { return service.run(); }
}
