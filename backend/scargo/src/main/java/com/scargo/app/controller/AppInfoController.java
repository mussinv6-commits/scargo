package com.scargo.app.controller;

import com.scargo.app.dto.AppDtos.NotificationItem;
import com.scargo.app.dto.AppDtos.PlaceItem;
import com.scargo.app.security.CurrentAccount;
import com.scargo.app.service.AppNotificationService;
import com.scargo.app.service.PlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 26.10.03 2차: 알림·정보 조회
 *  GET  /api/app/notifications?size=50          08 알림
 *  POST /api/app/notifications/read-all         08 모두 읽음
 *  GET  /api/app/places?type=HUB&lat=&lng=&radiusKm=30&q=&limit=50   09 정보 조회 지도
 *  GET  /api/app/places/{type}/{id}             05·09 지도 핀 상세
 */
@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class AppInfoController {

    private final AppNotificationService notificationService;
    private final PlaceService placeService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** fix_1600: 시연 주행(GPS 시뮬레이션) 버튼 노출 여부.
     *  scargo.app.demo-sim=true(개발 기본) 이면 모두, 아니면(운영) ADMIN 이거나 시연 계정(아이디 demo… 또는 시드 시연 기사 driver01·branch01)만 */
    @org.springframework.beans.factory.annotation.Value("${scargo.app.demo-sim:false}")
    private boolean demoSim;

    @GetMapping("/demo-sim")
    public Map<String, Object> demoSim() {
        Map<String, Object> a = jdbc.queryForMap("SELECT user_id, user_type FROM accounts WHERE account_id = ?", CurrentAccount.id());
        String uid = String.valueOf(a.get("user_id")).toLowerCase();
        boolean demoAccount = uid.startsWith("demo") || java.util.Set.of("driver01", "branch01").contains(uid);
        boolean on = demoSim || "ADMIN".equals(a.get("user_type")) || demoAccount;
        return Map.of("enabled", on, "reason", demoSim ? "설정(scargo.app.demo-sim)" : on ? ("ADMIN".equals(a.get("user_type")) ? "관리자" : "시연 계정") : "꺼짐");
    }

    @GetMapping("/notifications")
    public List<NotificationItem> notifications(@RequestParam(value = "size", defaultValue = "50") int size) {
        return notificationService.list(CurrentAccount.id(), size);
    }

    @PostMapping("/notifications/read-all")
    public Map<String, Integer> readAll() {
        return Map.of("updated", notificationService.readAll(CurrentAccount.id()));
    }

    @GetMapping("/places")
    public List<PlaceItem> places(@RequestParam(value = "type", defaultValue = "HUB") String type,
                                  @RequestParam(value = "lat", required = false) Double lat,
                                  @RequestParam(value = "lng", required = false) Double lng,
                                  @RequestParam(value = "radiusKm", required = false) Double radiusKm,
                                  @RequestParam(value = "q", required = false) String q,
                                  @RequestParam(value = "limit", required = false) Integer limit) {
        return placeService.find(type, lat, lng, radiusKm, q, limit);
    }

    /** 지도 핀 상세: GET /api/app/places/{type}/{id}?lat=&lng=  (type = HUB|TERMINAL|WEIGHBRIDGE|CHECKPOINT|TOLL|HEIGHT) */
    @GetMapping("/places/{type}/{id}")
    public PlaceItem place(@PathVariable("type") String type, @PathVariable("id") String id,
                           @RequestParam(value = "lat", required = false) Double lat,
                           @RequestParam(value = "lng", required = false) Double lng) {
        return placeService.detail(type, id, lat, lng);
    }
}
