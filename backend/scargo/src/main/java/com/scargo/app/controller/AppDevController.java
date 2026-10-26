package com.scargo.app.controller;

import com.scargo.app.dto.AppDtos.OrderDetail;
import com.scargo.app.security.CurrentAccount;
import com.scargo.app.service.TransportOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 26.10.03 2차: PC 테스트 전용 API. application.yml 의 scargo.app.dev-tools=true 일 때만 등록된다.
 *  GET  /api/app/dev/enabled                  → 앱이 "게이트아웃 시뮬레이션" 버튼을 보여줄지 판단
 *  POST /api/app/dev/orders/{id}/gate-out     → OCR 장비 없이 OUT 게이트 통과 기록 생성
 * 발표/운영 환경에서는 dev-tools=false 로 끄세요.
 */
@RestController
@RequestMapping("/api/app/dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scargo.app.dev-tools", havingValue = "true")
public class AppDevController {

    private final TransportOrderService orderService;

    @GetMapping("/enabled")
    public Map<String, Boolean> enabled() {
        return Map.of("enabled", true);
    }

    @PostMapping("/orders/{id}/gate-out")
    public OrderDetail gateOut(@PathVariable("id") Long id) {
        return orderService.devGateOut(CurrentAccount.id(), id);
    }
}
