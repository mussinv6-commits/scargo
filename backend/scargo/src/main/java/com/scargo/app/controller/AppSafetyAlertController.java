package com.scargo.app.controller;

import com.scargo.amr.controller.SafetyAlertController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 26.10.29 기사 앱: 허브 화재 경보 진행 상태 (읽기 전용)
 *  GET /api/app/safety/alert → {active:false} 또는 {active:true, kind:'hub'|'batt', label, since, ...}
 *  /api/amr/** 는 허브 앱 출입증 · 관제 권한 전용이라, 기사 앱은 이 주소로 같은 상태를 읽는다
 */
@RestController
public class AppSafetyAlertController {
    private final SafetyAlertController safety;

    public AppSafetyAlertController(SafetyAlertController safety) {
        this.safety = safety;
    }

    @GetMapping("/api/app/safety/alert")
    public Map<String, Object> current() {
        return safety.current();
    }
}
