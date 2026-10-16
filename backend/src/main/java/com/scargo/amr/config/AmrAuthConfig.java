package com.scargo.amr.config;

import com.scargo.amr.service.AmrAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 26.10.16 AMR 관리자 OTP 잠금
 *  - /api/amr/** 요청은 X-AMR-Token 헤더(OTP 통과 후 받은 24시간 출입증)가 있어야 통과
 *  - 팀 공통 보안 설정(SecurityConfig)은 건드리지 않고 AMR 경로에만 인터셉터를 건다
 *  - 예외: /api/amr/auth/** (로그인 자체), /api/amr/health (연결 확인), OPTIONS (CORS 사전 요청)
 */
@Configuration
@RequiredArgsConstructor
public class AmrAuthConfig implements WebMvcConfigurer {

    public static final String HEADER = "X-AMR-Token";

    private final AmrAuthService auth;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
                if ("OPTIONS".equalsIgnoreCase(req.getMethod())) return true;
                if (auth.check(req.getHeader(HEADER)) != null) return true;
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setCharacterEncoding("UTF-8");
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write("{\"error\":\"AMR_AUTH\",\"message\":\"AMR 관리자 인증이 필요합니다\"}");
                return false;
            }
        }).addPathPatterns("/api/amr/**").excludePathPatterns("/api/amr/auth/**", "/api/amr/health");
    }
}
