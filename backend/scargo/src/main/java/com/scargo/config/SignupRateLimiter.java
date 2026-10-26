package com.scargo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** fix_1330: 비로그인 업체 등록(POST /api/companies/signup) IP 별 제한. 메모리 보관(서버 재시작 시 초기화, 단일 인스턴스 기준) */
@Component
public class SignupRateLimiter {
    private final int limit;
    private final long windowMs;
    private final ConcurrentHashMap<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public SignupRateLimiter(@Value("${scargo.signup-rate.limit:5}") int limit, @Value("${scargo.signup-rate.window-minutes:60}") long windowMinutes) {
        this.limit = limit; this.windowMs = windowMinutes * 60_000L;
    }

    public boolean tryAcquire(String ip) {
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(ip == null ? "?" : ip, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > windowMs) q.pollFirst();
            if (q.size() >= limit) return false;
            q.addLast(now);
        }
        if (hits.size() > 10_000) hits.entrySet().removeIf(e -> { synchronized (e.getValue()) { return e.getValue().isEmpty() || now - e.getValue().peekLast() > windowMs; } });
        return true;
    }
}
