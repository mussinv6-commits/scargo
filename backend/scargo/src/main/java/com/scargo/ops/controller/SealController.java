package com.scargo.ops.controller;

import com.scargo.ops.service.SealService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 26.10.20 씰 번호 앱 → 웹 */
@RestController
@RequiredArgsConstructor
public class SealController {
    private final SealService svc;

    /** 웹 W-02·W-04·W-09 씰 현황 (화면은 5초 폴링) */
    @GetMapping("/api/web/seals")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','HUB_OP','CENTER_OP','CARRIER')")
    public Map<String, Object> web() { return svc.overviewForMe(); }   // 26.10.20: 운송사 담당자(W-02)는 자기 회사 오더·차량 것만

    /** fix_1630: W-07 오더별 씰 확인 이력 */
    @GetMapping("/api/web/orders/{id}/seal-history")
    @PreAuthorize("hasAnyRole('ADMIN','CONTROL','HUB_OP','CENTER_OP','CARRIER')")
    public Map<String, Object> orderHistory(@PathVariable("id") Long id) { return svc.orderSealHistory(id); }

    /** 허브 앱 씰 목록 */
    @GetMapping("/api/app/hub/seals")
    @PreAuthorize("hasRole('HUB_OP')")
    public Map<String, Object> hub() { return svc.overview(); }

    /** 컨테이너 기사 T-02 */
    @GetMapping("/api/app/container/current")
    @PreAuthorize("hasRole('CONTAINER_DRIVER')")
    public Map<String, Object> current() { return svc.myCurrentOrder(); }

    /** body: { containerNo, sealNo, checksOk } */
    @PostMapping("/api/app/container/orders/{id}/pickup")
    @PreAuthorize("hasRole('CONTAINER_DRIVER')")
    public Map<String, Object> pickup(@PathVariable("id") Long id, @jakarta.validation.Valid @RequestBody com.scargo.ops.dto.SealRequests.PickupRequest b) {
        // 26.10.21 씰번호 필수 (@NotBlank·형식 검사 → 400)
        return svc.containerPickup(id, (b.containerNo() == null || b.containerNo().isBlank() ? null : b.containerNo().trim().toUpperCase()), com.scargo.ops.dto.SealRequests.normalize(b.sealNo()), Boolean.TRUE.equals(b.checksOk()));
    }
}
