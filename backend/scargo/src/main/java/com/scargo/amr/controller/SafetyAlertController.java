package com.scargo.amr.controller;

import com.scargo.amr.service.SafetyDriverNotifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * 26.10.29 긴급 경보(화재) 공용 상태 — 허브 관제(웹)가 시작·종료하고, 앱 · S Cargo 가 읽어 붉은 화면으로 표시한다.
 *  GET  /api/amr/safety/alert          → {active:false} 또는 {active:true, kind, label, stage, since, evac, total, temp}  (로그인한 누구나)
 *  POST /api/amr/safety/alert          → 시작 · 단계 갱신 {kind:'hub'|'batt', label, stage, evac, total, temp}  (관리 권한)
 *  POST /api/amr/safety/alert/end      → 화재 종료 (관리 권한) — 종료하면 공지 글(해제)도 남긴다
 *  GET  /api/amr/safety/history        → 최근 경보 기록 (S Cargo 공지 목록용)
 * 서버를 다시 켜면 진행 중 경보는 사라진다 (메모리 보관). TODO(노트북): @PreAuthorize 식은 AmrController 의 VIEW / MANAGE 상수와 같게 맞출 것.
 */
@RestController
@RequestMapping("/api/amr/safety")
public class SafetyAlertController {
    private static final Object LOCK = new Object();
    private static Map<String, Object> active = null;
    private static final Deque<Map<String, Object>> HISTORY = new ArrayDeque<>();
    private final SafetyDriverNotifier drivers; // 26.10.29 기사 앱 알림 (발령 · 종료 때 한 번씩)

    public SafetyAlertController(SafetyDriverNotifier drivers) {
        this.drivers = drivers;
    }

    @GetMapping("/alert")
    public Map<String, Object> current() {
        synchronized (LOCK) { return active == null ? Map.of("active", false) : new LinkedHashMap<>(active); }
    }

    @PostMapping("/alert")
    @PreAuthorize("hasAnyRole('ADMIN','AMR_ADMIN','HUB_OP','AMR_DEVICE')") // 26.10.30 AmrController.MANAGE 와 같게: 폰 앱(출입증)에서 누른 화재도 서버에 알릴 수 있게
    public Map<String, Object> start(@RequestBody Map<String, Object> req) {
        String kind = String.valueOf(req.getOrDefault("kind", "hub"));
        if (!kind.equals("hub") && !kind.equals("batt")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind 는 hub 또는 batt");
        boolean fresh;
        Map<String, Object> out;
        synchronized (LOCK) {
            Map<String, Object> a = active != null && kind.equals(active.get("kind")) ? active : new LinkedHashMap<>();
            fresh = a.isEmpty();
            if (fresh) { a.put("since", System.currentTimeMillis()); addHistory(kind, String.valueOf(req.getOrDefault("label", "")), "발령", 0); }
            a.put("active", true); a.put("kind", kind);
            for (String k : List.of("label", "code", "zone", "stage", "evac", "total", "temp")) if (req.containsKey(k)) a.put(k, req.get(k));
            active = a;
            out = new LinkedHashMap<>(a);
        }
        if (fresh) drivers.started(kind, String.valueOf(req.getOrDefault("label", ""))); // 새 경보일 때만
        return out;
    }

    @PostMapping("/alert/end")
    @PreAuthorize("hasAnyRole('ADMIN','AMR_ADMIN','HUB_OP','AMR_DEVICE')") // 26.10.30 AmrController.MANAGE 와 같게: 폰 앱(출입증)에서 누른 화재도 서버에 알릴 수 있게
    public Map<String, Object> end() {
        String endedKind = null, endedLabel = null;
        synchronized (LOCK) {
            if (active != null) {
                long sec = (System.currentTimeMillis() - ((Number) active.get("since")).longValue()) / 1000;
                endedKind = String.valueOf(active.get("kind")); endedLabel = String.valueOf(active.getOrDefault("label", ""));
                addHistory(endedKind, endedLabel, "해제", sec);
            }
            active = null;
        }
        if (endedKind != null) drivers.ended(endedKind, endedLabel); // 기사 앱에 화재 종료 알림
        return Map.of("active", false);
    }

    // 26.10.30 비상 정지 공용 상태 — 웹 허브 관제 · 폰 앱이 서로 같은 정지 목록을 본다 (메모리 보관, 서버를 다시 켜면 비워짐)
    private static volatile List<String> estopCodes = List.of();

    @GetMapping("/estop")
    public Map<String, Object> estopNow() { return Map.of("codes", estopCodes); }

    @PostMapping("/estop")
    @PreAuthorize("hasAnyRole('ADMIN','AMR_ADMIN','HUB_OP','AMR_DEVICE')")
    @SuppressWarnings("unchecked")
    public Map<String, Object> estopSet(@RequestBody Map<String, Object> req) {
        List<String> codes = new ArrayList<>();
        Object o = req.get("codes");
        if (o instanceof List<?> l) for (Object x : l) if (x != null) codes.add(String.valueOf(x));
        estopCodes = List.copyOf(codes);
        return Map.of("codes", estopCodes);
    }

    @GetMapping("/history")
    public List<Map<String, Object>> history() {
        synchronized (LOCK) { return new ArrayList<>(HISTORY); }
    }

    private static void addHistory(String kind, String label, String type, long sec) {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("at", System.currentTimeMillis()); h.put("kind", kind); h.put("label", label); h.put("type", type); h.put("secs", sec);
        h.put("title", "[자동] " + (kind.equals("batt") ? label + " 배터리 화재 " : "허브 화재 경보 ") + type);
        HISTORY.addFirst(h);
        while (HISTORY.size() > 50) HISTORY.removeLast();
    }
}
