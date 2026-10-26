package com.scargo.ops.controller;

import com.scargo.ops.entity.ContainerSeal;
import com.scargo.ops.entity.HubDock;
import com.scargo.ops.service.HubOpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 26.10.20 2차 허브 앱 (HUB_OP 로그인만으로 진입, 씰 불일치 개봉 승인만 비밀번호 재인증) */
@RestController
@RequestMapping("/api/app/hub")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HUB_OP')")
public class AppHubController {
    private final HubOpsService svc;

    @GetMapping("/docks") public List<HubDock> docks() { return svc.myDocks(); }
    @GetMapping("/seals/{id}") public Map<String, Object> seal(@PathVariable("id") Long id) { return svc.seal(id); }
    /** body: { observedSealNo, observedCondition: INTACT|DAMAGED|MISSING } */
    @PostMapping("/seals/{id}/receive") public Map<String, Object> receive(@PathVariable("id") Long id, @jakarta.validation.Valid @RequestBody com.scargo.ops.dto.SealRequests.HubReceiveRequest b) {
        // 26.10.21 씰번호 필수 (MISSING 제외) · 형식 검사 → 400
        String seal = b.observedSealNo() == null || b.observedSealNo().isBlank() ? null : com.scargo.ops.dto.SealRequests.normalize(b.observedSealNo());
        return svc.receive(id, seal, b.observedCondition()); }
    @PostMapping("/seals/{id}/approve-open") public ContainerSeal approve(@PathVariable("id") Long id, @RequestBody Map<String, String> b) { return svc.approveOpen(id, b.get("password")); }
    /** 보류 판단. body: { action: RELEASE|KEEP, password(RELEASE 필수), reclassRequired } */
    @PostMapping("/seals/{id}/hold") public Map<String, Object> hold(@PathVariable("id") Long id, @RequestBody Map<String, Object> b) {
        return svc.holdDecision(id, (String) b.get("action"), (String) b.get("password"), b.get("reclassRequired") == null ? null : Boolean.valueOf(b.get("reclassRequired").toString()));
    }
    /** 분실 확정 (seal_events LOST + container_seals LOST). body: { reason } */
    @PostMapping("/seals/{id}/lost") public Map<String, Object> lost(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, String> b) { return svc.reportLost(id, b == null ? null : b.get("reason")); }
    @PostMapping("/units/{unitId}/reclass-done") public Map<String, Object> reclass(@PathVariable("unitId") String unitId) { return svc.reclassDone(unitId); }
}
