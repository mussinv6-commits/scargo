package com.scargo.app.security;

import com.scargo.security.AuthenticatedAccountPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 26.10.03 2차: 현재 로그인한 계정 ID 꺼내기 (토큰·세션 어느 쪽으로 로그인해도 동일) */
public final class CurrentAccount {

    private CurrentAccount() {
    }

    public static Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedAccountPrincipal p) {
            return p.getId();
        }
        throw new IllegalStateException("로그인이 필요합니다.");
    }
}
