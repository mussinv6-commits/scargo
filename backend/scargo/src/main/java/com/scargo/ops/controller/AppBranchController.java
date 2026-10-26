package com.scargo.ops.controller;

import com.scargo.ops.service.BranchService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 26.10.20 지선 운송기사 앱 (P-01 ~ P-06). 응답의 phase = 화면 단계 */
@RestController
@RequestMapping("/api/app/branch")
@RequiredArgsConstructor
@PreAuthorize("hasRole('BRANCH_DRIVER')")
public class AppBranchController {
    private final BranchService svc;

    @GetMapping("/current") public Map<String, Object> current() { return svc.current(); }
    /** 허브 도착 → hub_arrived_at (phase AT_HUB) */
    @PostMapping("/{id}/hub-arrive") public Map<String, Object> hubArrive(@PathVariable("id") Long id) { return svc.hubArrive(id); }
    @PostMapping("/{id}/scan") public Map<String, Object> scan(@PathVariable("id") Long id, @RequestBody Map<String, String> b) {
        if (!"LOAD".equals(b.getOrDefault("type", "LOAD"))) throw new IllegalArgumentException("지금은 LOAD 스캔만 지원합니다."); // TODO: UNLOAD 스캔(롤테이너·박스 단위)
        return svc.scanLoad(id, b.get("unitId"));
    }
    @PostMapping("/{id}/confirm-load") public Map<String, Object> confirm(@PathVariable("id") Long id) { return svc.confirmLoad(id); }
    @PostMapping("/{id}/depart") public Map<String, Object> depart(@PathVariable("id") Long id, @jakarta.validation.Valid @RequestBody com.scargo.ops.dto.SealRequests.DepartRequest b) { return svc.depart(id, com.scargo.ops.dto.SealRequests.normalize(b.sealNo())); } // 26.10.21 씰번호 필수
    @PostMapping("/{id}/arrive") public Map<String, Object> arrive(@PathVariable("id") Long id) { return svc.arrive(id); }
    @PostMapping("/{id}/unloaded") public Map<String, Object> unloaded(@PathVariable("id") Long id) { return svc.unloaded(id); }
    /** body: { "damagedItemIds": [itemId, ...] } */
    @PostMapping("/{id}/inspected") public Map<String, Object> inspected(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, List<Long>> b) {
        return svc.inspected(id, b == null ? List.of() : b.getOrDefault("damagedItemIds", List.of()));
    }
    @PostMapping("/{id}/receive") public Map<String, Object> receive(@PathVariable("id") Long id) { return svc.receive(id); }
}
