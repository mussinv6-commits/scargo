package com.scargo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 26.10.21 역할 분리 (서버): S-Bot(AMR) 관리자(ROLE_AMR_ADMIN)는 AMR API 와 로그인/로그아웃만 쓸 수 있다.
 * 그 밖의 /api/** (S Cargo 관리자·업무 API, /api/admin/** 포함) 는 403.
 * 권한 표(hasRole('ADMIN'))에서 이미 빠지지만, 새 API 가 권한 표시를 빠뜨려도 막히도록 한 번 더 거는 기본 차단(허용 목록 방식).
 */
@Component
public class SbotScopeFilter extends OncePerRequestFilter {

    static boolean allowed(String method, String path) {
        if (path.startsWith("/api/amr/") || path.equals("/api/amr")) return true;
        if (path.equals("/api/accounts/login") || path.equals("/api/accounts/logout")) return true;
        if ("GET".equals(method) && (path.startsWith("/api/notices") || path.equals("/api/app/health"))) return true;
        return !path.startsWith("/api/");   // 정적 파일·Swagger 등 API 가 아닌 경로
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        boolean sbot = a != null && a.getAuthorities().stream().anyMatch(g -> "ROLE_AMR_ADMIN".equals(g.getAuthority()));
        String path = req.getRequestURI().substring(req.getContextPath().length());
        if (sbot && !"OPTIONS".equalsIgnoreCase(req.getMethod()) && !allowed(req.getMethod(), path)) {
            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            res.setCharacterEncoding("UTF-8");
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"error\":\"SBOT_SCOPE\",\"message\":\"S-Bot 관리자는 S Cargo 기능을 사용할 수 없습니다\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
