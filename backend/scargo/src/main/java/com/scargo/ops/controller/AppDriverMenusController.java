package com.scargo.ops.controller;

import com.scargo.ops.service.AppDriverMenusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/** 26.11.01 기사앱 추가 메뉴 API. @RequestParam/@PathVariable 이름 필수 (STS -parameters 없음) */
@RestController
@RequestMapping("/api/app/menus")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CONTAINER_DRIVER','BRANCH_DRIVER')")
public class AppDriverMenusController {
    private final AppDriverMenusService svc;

    @GetMapping("/incidents/runs")
    public List<Map<String, Object>> incidentRuns() { return svc.myRunsForReport(); }

    @GetMapping("/incidents")
    public List<Map<String, Object>> incidents() { return svc.myIncidents(); }

    @PostMapping(value = "/incidents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> report(
            @RequestParam(name = "category") String category,
            @RequestParam(name = "severity", required = false) String severity,
            @RequestParam(name = "message") String message,
            @RequestParam(name = "locationText", required = false) String locationText,
            @RequestParam(name = "lat", required = false) String lat,
            @RequestParam(name = "lng", required = false) String lng,
            @RequestParam(name = "orderId", required = false) String orderId,
            @RequestParam(name = "assignmentId", required = false) String assignmentId,
            @RequestParam(name = "files", required = false) List<MultipartFile> files) throws Exception {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("category", category); b.put("severity", severity); b.put("message", message);
        b.put("locationText", locationText); b.put("lat", lat); b.put("lng", lng);
        b.put("orderId", orderId); b.put("assignmentId", assignmentId);
        return svc.reportIncident(b, files == null ? List.of() : files);
    }

    @GetMapping("/support")
    public Map<String, Object> support() { return svc.supportHome(); }

    @PostMapping("/support/inquiries")
    public Map<String, Object> inquiry(@RequestBody Map<String, Object> body) { return svc.createInquiry(body); }

    @GetMapping("/schedule")
    public Map<String, Object> schedule(@RequestParam(name = "month", required = false) String month) {
        return svc.schedule(month);
    }

    @GetMapping("/settlements")
    public Map<String, Object> settlements(@RequestParam(name = "month", required = false) String month) {
        return svc.settlements(month);
    }

    @GetMapping("/documents")
    public List<Map<String, Object>> documents() { return svc.documents(); }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upsertDoc(
            @RequestParam(name = "documentId", required = false) String documentId,
            @RequestParam(name = "docType") String docType,
            @RequestParam(name = "title", required = false) String title,
            @RequestParam(name = "expiresAt", required = false) String expiresAt,
            @RequestParam(name = "notes", required = false) String notes,
            @RequestParam(name = "file", required = false) MultipartFile file) throws Exception {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("documentId", documentId); b.put("docType", docType); b.put("title", title);
        b.put("expiresAt", expiresAt); b.put("notes", notes);
        return svc.upsertDocument(b, file);
    }

    @DeleteMapping("/documents/{id}")
    public Map<String, Object> deleteDoc(@PathVariable(name = "id") Long id) {
        svc.deleteDocument(id); return Map.of("ok", true);
    }

    @GetMapping("/logbook")
    public Map<String, Object> logbook(@RequestParam(name = "day", required = false) String day) {
        return svc.logbook(day);
    }

    @GetMapping("/nearby")
    public Map<String, Object> nearby(
            @RequestParam(name = "lat", required = false) Double lat,
            @RequestParam(name = "lng", required = false) Double lng) {
        return svc.nearbyAids(lat, lng);
    }

    @GetMapping("/inspections/history")
    public Map<String, Object> inspectionHistory(
            @RequestParam(name = "range", required = false) String range,
            @RequestParam(name = "result", required = false) String result) {
        return svc.inspectionHistory(range, result);
    }

    @GetMapping("/settings")
    public Map<String, Object> settings() { return svc.getSettings(); }

    @PutMapping("/settings")
    public Map<String, Object> saveSettings(@RequestBody Map<String, Object> body) { return svc.saveSettings(body); }
}
