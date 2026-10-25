package com.scargo.amr.config;

import com.scargo.amr.service.AmrAuthService;
import com.scargo.security.AuthenticatedAccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 26.10.16 AMR 관리자 인증 잠금
 *  - /api/amr/** 는 둘 중 하나여야 통과
 *     ① 웹: 팀 공통 로그인 세션이 ADMIN
 *     ② 앱: X-AMR-Token 헤더 (앱 연결 코드로 받은 24시간 출입증)
 *  - 팀 공통 보안 설정(SecurityConfig)은 건드리지 않고 AMR 경로에만 인터셉터를 건다
 *  - 예외: /api/amr/auth/** (로그인 자체), /api/amr/health (연결 확인), OPTIONS (CORS 사전 요청)
 */
@Configuration
@RequiredArgsConstructor
public class AmrAuthConfig implements WebMvcConfigurer {

    public static final String HEADER = "X-AMR-Token";

    private final AmrAuthService auth;

    /** 팀 공통 로그인(세션)으로 들어온 ADMIN 이면 아이디, 아니면 null */
    public static String adminId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) return null;
        boolean admin = a.getAuthorities().stream().anyMatch((g) -> "ROLE_ADMIN".equals(g.getAuthority()));
        // 26.10.22 S-Bot 관리자: 아이디가 sbot 으로 시작하면 (계정 권한이 ADMIN 이 아니어도) AMR · S-Bot API 를 쓸 수 있다
        if (!admin && a.getPrincipal() instanceof AuthenticatedAccountPrincipal p && p.getUserId() != null
                && p.getUserId().toLowerCase().startsWith("sbot")) {
            admin = true;
        }
        return admin ? a.getName() : null;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
                if ("OPTIONS".equalsIgnoreCase(req.getMethod())) return true;
                if (adminId() != null) return true;                       // ① 웹 관리자
                if (auth.check(req.getHeader(HEADER)) != null) return true; // ② 앱 토큰
                if (req.getRequestURI().endsWith("/photo") && auth.check(req.getParameter("t")) != null) return true; // ③ 앱 사진 (이미지 태그는 헤더를 못 보냄)
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setCharacterEncoding("UTF-8");
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"error\":\"AMR_AUTH\",\"message\":\"AMR 관리자 인증이 필요합니다\"}");
                return false;
            }
        }).addPathPatterns("/api/amr/**").excludePathPatterns("/api/amr/auth/**", "/api/amr/health");
    }
}
