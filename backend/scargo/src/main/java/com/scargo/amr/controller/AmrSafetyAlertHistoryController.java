package com.scargo.amr.controller;

import com.scargo.amr.service.AmrSafetyAlertService;
import com.scargo.entity.Account;
import com.scargo.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 26.11.01 S-Bot 「AMR 알림 내역」
 *  GET  /api/amr/alerts
 *  POST /api/amr/alerts/{id}/read
 *  POST /api/amr/alerts/read-all
 *  26.11.01 fix: @RequestParam 이름을 명시 (컴파일 -parameters 없이도 바인딩)
 */
@RestController
@RequestMapping("/api/amr/alerts")
@PreAuthorize("hasAnyRole('ADMIN','HUB_OP','AMR_ADMIN')")
public class AmrSafetyAlertHistoryController {
    private final AmrSafetyAlertService service;
    private final AccountRepository accounts;

    public AmrSafetyAlertHistoryController(AmrSafetyAlertService service, AccountRepository accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "read", required = false) String read,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        // 빈 문자열(type=&read=)도 필터 없음으로 취급
        if (type != null && type.isBlank()) type = null;
        if (read != null && read.isBlank()) read = null;
        if (q != null && q.isBlank()) q = null;
        return service.list(currentAccountId(), type, read, q, page, size);
    }

    @PostMapping("/{id}/read")
    public Map<String, Object> readOne(@PathVariable("id") Long id) {
        Long aid = currentAccountId();
        if (aid == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다");
        service.markRead(aid, id);
        return Map.of("ok", true);
    }

    @PostMapping("/read-all")
    public Map<String, Object> readAll() {
        Long aid = currentAccountId();
        if (aid == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다");
        return Map.of("ok", true, "updated", service.markAllRead(aid));
    }

    private Long currentAccountId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        String name = auth.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) return null;
        return accounts.findByUserId(name).map(Account::getAccountId).orElse(null);
    }
}