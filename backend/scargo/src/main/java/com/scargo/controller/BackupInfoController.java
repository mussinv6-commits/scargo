package com.scargo.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// 26.10.24 자동 백업 안내: 주기 문구 + 백업 폴더의 가장 최근 .dump 파일 시각 (읽기 전용, ADMIN · CONTROL)
//  GET /api/ops/backup-info -> { schedule, lastBackupAt(ISO, 없으면 null), fileCount }
@RestController
@RequestMapping("/api/ops")
@PreAuthorize("hasAnyRole('ADMIN', 'CONTROL')")
public class BackupInfoController {

    private final String dir;
    private final String schedule;

    public BackupInfoController(@Value("${scargo.backup.dir:../../백업}") String dir,
                                @Value("${scargo.backup.schedule:매일 02:00 자동 백업 · 최근 7일 보관}") String schedule) {
        this.dir = dir;
        this.schedule = schedule;
    }

    @GetMapping("/backup-info")
    public ResponseEntity<Map<String, Object>> info() {
        long newest = 0;
        int count = 0;
        File[] files = new File(dir).listFiles((d, n) -> n.endsWith(".dump"));
        if (files != null) {
            for (File f : files) {
                count++;
                newest = Math.max(newest, f.lastModified());
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("schedule", schedule);
        body.put("lastBackupAt", newest > 0 ? Instant.ofEpochMilli(newest).toString() : null);
        body.put("fileCount", count);
        return ResponseEntity.ok(body);
    }
}
