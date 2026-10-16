package com.scargo.amr.controller;

import com.scargo.amr.dto.AmrDtos.*;
import com.scargo.amr.service.AmrService;
import com.scargo.amr.service.HubVehicleService;
import com.scargo.amr.service.AmrKpiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

// 26.10.14 AMR 연동 API (SecurityConfig 에서 /api/amr/** 공개)
//  웹 관제 화면: telemetry · predictions · start · done 저장
//  앱 · 웹 공통: state 조회(2초마다), settings(관리자 운영 기준: 정비 · 충전)
//  ※ @PathVariable · @RequestParam 이름을 직접 적음 (STS 컴파일은 파라미터 이름 정보가 없어서 생략하면 500 오류)
@RestController
@RequestMapping("/api/amr")
@RequiredArgsConstructor
public class AmrController {

    private final AmrService service;
    private final HubVehicleService vehicles;
    private final AmrKpiService kpi;

    @GetMapping("/health")
    public Map<String, Object> health() { return Map.of("ok", true); }

    @GetMapping("/state")
    public StateView state() { return service.state(); }

    @PostMapping("/telemetry")
    public Map<String, Object> telemetry(@RequestBody TelemetryRequest req) { return Map.of("saved", service.saveTelemetry(req)); }

    @PostMapping("/predictions")
    public MaintView predict(@RequestBody PredictionRequest req) { return service.predict(req); }

    @PostMapping("/maintenance/{id}/approve")
    public MaintView approve(@PathVariable("id") Long id, @RequestBody(required = false) ApproveRequest req) {
        return service.approve(id, req == null ? null : req.by());
    }

    @PostMapping("/maintenance/{id}/start")
    public MaintView start(@PathVariable("id") Long id) { return service.start(id); }

    @PostMapping("/maintenance/{id}/done")
    public MaintView done(@PathVariable("id") Long id) { return service.done(id); }

    @GetMapping("/maintenance")
    public List<MaintView> maintenance(@RequestParam(name = "limit", defaultValue = "10") int limit) { return service.maintenanceList(limit); }

    @GetMapping("/settings")
    public SettingsView settings() { return service.settings(); }

    @PutMapping("/settings")
    public SettingsView updateSettings(@RequestBody SettingsRequest req) { return service.updateSettings(req); }

    @PostMapping("/events")
    public Map<String, Object> event(@RequestBody EventRequest req) { service.addEvent(req); return Map.of("ok", true); }

    @GetMapping("/robots/{code}/telemetry")
    public List<SensorPoint> history(@PathVariable("code") String code, @RequestParam(name = "minutes", defaultValue = "5") int minutes) {
        return service.history(code, minutes);
    }

    // ── 26.10.15 허브 입출차 번호판 OCR ──
    @PostMapping("/vehicles/scan")
    public VehicleView scanVehicle(@RequestBody VehicleScanRequest req) { return vehicles.scan(req); }

    @GetMapping("/vehicles")
    public List<VehicleView> vehicleLogs(@RequestParam(name = "limit", defaultValue = "20") int limit) { return vehicles.recent(limit); }

    // 26.10.16 인식 사진 (앱은 출입증을 ?t= 로 붙여 요청: 이미지 태그는 헤더를 못 보내서)
    @GetMapping("/vehicles/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable("id") Long id) {
        return ResponseEntity.ok().header("Content-Type", "image/jpeg").header("Cache-Control", "max-age=3600").body(vehicles.photo(id));
    }

    @GetMapping("/vehicles/registry")
    public List<RegistryView> registry() { return vehicles.registry(); }

    // ── 26.10.15 KPI ──
    @PostMapping("/tasks")
    public Map<String, Object> task(@RequestBody TaskRequest req) { return kpi.saveTask(req); }

    @GetMapping("/kpi")
    public KpiView kpi() { return kpi.kpi(); }

    // 26.10.16 실패 이유(예: OCR 서버 연결 실패 · 판독 오류)를 화면에 그대로 보여 주려고 메시지를 JSON으로 돌려준다
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> fail(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "요청 실패" : e.getReason()));
    }
}
