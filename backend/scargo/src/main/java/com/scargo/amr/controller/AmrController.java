package com.scargo.amr.controller;

import com.scargo.amr.dto.AmrDtos.*;
import com.scargo.amr.service.AmrService;
import com.scargo.amr.service.HubVehicleService;
import com.scargo.amr.service.AmrKpiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

// 26.10.14 AMR 연동 API
//  웹 관제 화면: telemetry · predictions · start · done 저장
//  앱 · 웹 공통: state 조회(2초마다), settings(관리자 운영 기준: 정비 · 충전)
//  ※ @PathVariable · @RequestParam 이름을 직접 적음 (STS 컴파일은 파라미터 이름 정보가 없어서 생략하면 500 오류)
// 26.10.22 허브별 권한 (기존: ADMIN 세션 또는 앱 출입증이면 전부 허용, 허브 구분 없음)
//  - 조회(VIEW):   ADMIN · CONTROL(전체 허브) · HUB_OP(소속 허브) · 허브 앱(출입증의 허브)
//  - 변경(MANAGE): ADMIN · HUB_OP(소속 허브) · 허브 앱. CONTROL은 조회만, CENTER_OP 등은 접근 불가
//  - hubId 파라미터를 빼면 HUB_OP · 허브 앱은 소속 허브, ADMIN · CONTROL은 기본 허브(곤지암)
//  - 여기서는 역할만 거르고, 허브 범위는 서비스가 AmrAccessPolicy 로 확인한다
@RestController
@RequestMapping("/api/amr")
@RequiredArgsConstructor
public class AmrController {

    static final String VIEW = "hasAnyRole('ADMIN', 'AMR_ADMIN', 'CONTROL', 'HUB_OP', 'AMR_DEVICE')";
    static final String MANAGE = "hasAnyRole('ADMIN', 'AMR_ADMIN', 'HUB_OP', 'AMR_DEVICE')";

    private final AmrService service;
    private final HubVehicleService vehicles;
    private final AmrKpiService kpi;

    @GetMapping("/health")
    public Map<String, Object> health() { return Map.of("ok", true); }

    // 26.10.22 화면의 허브 선택 목록 (볼 수 있는 허브만)
    @GetMapping("/hubs")
    @PreAuthorize(VIEW)
    public List<HubView> hubs() { return service.hubs(); }

    @GetMapping("/state")
    @PreAuthorize(VIEW)
    public StateView state(@RequestParam(name = "hubId", required = false) Long hubId) { return service.state(hubId); }

    @PostMapping("/telemetry")
    @PreAuthorize(MANAGE)
    public Map<String, Object> telemetry(@RequestParam(name = "hubId", required = false) Long hubId, @RequestBody TelemetryRequest req) {
        return Map.of("saved", service.saveTelemetry(hubId, req));
    }

    @PostMapping("/predictions")
    @PreAuthorize(MANAGE)
    public MaintView predict(@RequestParam(name = "hubId", required = false) Long hubId, @RequestBody PredictionRequest req) {
        return service.predict(hubId, req);
    }

    // 정비 기록은 정비 대상 로봇의 허브로 확인
    @PostMapping("/maintenance/{id}/approve")
    @PreAuthorize(MANAGE)
    public MaintView approve(@PathVariable("id") Long id, @RequestBody(required = false) ApproveRequest req) {
        return service.approve(id, req == null ? null : req.by());
    }

    @PostMapping("/maintenance/{id}/start")
    @PreAuthorize(MANAGE)
    public MaintView start(@PathVariable("id") Long id) { return service.start(id); }

    @PostMapping("/maintenance/{id}/done")
    @PreAuthorize(MANAGE)
    public MaintView done(@PathVariable("id") Long id) { return service.done(id); }

    @GetMapping("/maintenance")
    @PreAuthorize(VIEW)
    public List<MaintView> maintenance(@RequestParam(name = "hubId", required = false) Long hubId,
                                       @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return service.maintenanceList(hubId, limit);
    }

    @GetMapping("/settings")
    @PreAuthorize(VIEW)
    public SettingsView settings(@RequestParam(name = "hubId", required = false) Long hubId) { return service.settings(hubId); }

    @PutMapping("/settings")
    @PreAuthorize(MANAGE)
    public SettingsView updateSettings(@RequestParam(name = "hubId", required = false) Long hubId, @RequestBody SettingsRequest req) {
        return service.updateSettings(hubId, req);
    }

    @PostMapping("/events")
    @PreAuthorize(MANAGE)
    public Map<String, Object> event(@RequestParam(name = "hubId", required = false) Long hubId, @RequestBody EventRequest req) {
        service.addEvent(hubId, req);
        return Map.of("ok", true);
    }

    @GetMapping("/robots/{code}/telemetry")
    @PreAuthorize(VIEW)
    public List<SensorPoint> history(@PathVariable("code") String code,
                                     @RequestParam(name = "hubId", required = false) Long hubId,
                                     @RequestParam(name = "minutes", defaultValue = "5") int minutes) {
        return service.history(hubId, code, minutes);
    }

    // ── 26.10.15 허브 입출차 번호판 OCR ──
    @PostMapping("/vehicles/scan")
    @PreAuthorize(MANAGE)
    public VehicleView scanVehicle(@RequestParam(name = "hubId", required = false) Long hubId, @RequestBody VehicleScanRequest req) {
        return vehicles.scan(hubId, req);
    }

    @GetMapping("/vehicles")
    @PreAuthorize(VIEW)
    public List<VehicleView> vehicleLogs(@RequestParam(name = "hubId", required = false) Long hubId,
                                         @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return vehicles.recent(hubId, limit);
    }

    // 26.10.16 인식 사진 (앱은 출입증을 ?t= 로 붙여 요청: 이미지 태그는 헤더를 못 보내서)
    // 입출차 기록의 허브로 확인
    @GetMapping("/vehicles/{id}/photo")
    @PreAuthorize(VIEW)
    public ResponseEntity<byte[]> photo(@PathVariable("id") Long id) {
        return ResponseEntity.ok().header("Content-Type", "image/jpeg").header("Cache-Control", "max-age=3600").body(vehicles.photo(id));
    }

    @GetMapping("/vehicles/registry")
    @PreAuthorize(VIEW)
    public List<RegistryView> registry() { return vehicles.registry(); }

    // 26.10.20 씰 확인: 도크에서 스캔 · 입력한 씰 번호를 DB 기준 번호와 비교 (입출차 기록의 허브로 확인)
    @PostMapping("/vehicles/{id}/seal-check")
    @PreAuthorize(MANAGE)
    public VehicleView sealCheck(@PathVariable("id") Long id, @RequestBody SealCheckRequest req) { return vehicles.sealCheck(id, req.sealNo()); }

    // ── 26.10.15 KPI ──
    @PostMapping("/tasks")
    @PreAuthorize(MANAGE)
    public Map<String, Object> task(@RequestParam(name = "hubId", required = false) Long hubId, @RequestBody TaskRequest req) {
        return kpi.saveTask(hubId, req);
    }

    @GetMapping("/kpi")
    @PreAuthorize(VIEW)
    public KpiView kpi(@RequestParam(name = "hubId", required = false) Long hubId) { return kpi.kpi(hubId); }

    // 26.10.16 실패 이유(예: OCR 서버 연결 실패 · 판독 오류)를 화면에 그대로 보여 주려고 메시지를 JSON으로 돌려준다
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> fail(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "요청 실패" : e.getReason()));
    }

    // 26.10.22 허브 범위 밖 · 조회 전용 권한의 변경 요청 → 403 + 이유
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> denied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage() == null ? "권한 없음" : e.getMessage()));
    }
}
