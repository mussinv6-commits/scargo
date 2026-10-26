package com.scargo.ops.controller;

import com.scargo.ops.entity.OpsException;
import com.scargo.ops.service.ExceptionService;
import com.scargo.ops.service.HubOpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 26.10.20 2차 웹 컨트롤타워 API. 권한표 = 기획서 v2 p.20 (메뉴 숨김과 별개로 403) */
@RestController
@RequestMapping("/api/web")
@RequiredArgsConstructor
public class WebOpsController {
    private final HubOpsService hub;
    private final ExceptionService exceptions;

    /** W-04 허브 관제: ADMIN·CONTROL 조회, HUB_OP 처리 */
    @GetMapping("/hubs/{id}/monitor")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','HUB_OP')")
    public Map<String, Object> monitor(@PathVariable("id") Long id) { return hub.monitor(id); }

    /** W-09 예외 알림 센터: 운송사 담당자(CORPORATE_APPROVED)도 △ */
    @GetMapping("/exceptions")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','HUB_OP','CENTER_OP','CARRIER')")
    public List<OpsException> list() { return exceptions.list(); }

    /** 거점 긴급도 (v_site_urgency): CRITICAL 미해결 1건 이상이면 긴급 */
    @GetMapping("/site-urgency")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','HUB_OP','CENTER_OP')")
    public List<Map<String, Object>> urgency() { return exceptions.siteUrgency(); }

    /** 처리는 CONTROL(●) + 소속 거점 HUB_OP·CENTER_OP. ADMIN 은 조회만 */
    @PutMapping("/exceptions/{id}")
    @PreAuthorize("hasAnyRole('CONTROL','HUB_OP','CENTER_OP')")
    public OpsException advance(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, String> body) {
        return exceptions.advance(id, body == null ? null : body.get("note"));
    }

    /** W-08 △: HUB_OP 출고 도크 연결 → hub_dispatches.dock_id. 배차(운행 생성·기사 배정)는 운송사 담당자 — TODO: W-08 배차 API 는 다음 단계 */
    @PutMapping("/branch/{tripId}/out-dock")
    @PreAuthorize("hasRole('HUB_OP')")
    public Map<String, Object> linkDock(@PathVariable("tripId") Long tripId, @RequestBody Map<String, Long> body) {
        return hub.linkOutDock(tripId, body.get("dockId"));
    }
}
