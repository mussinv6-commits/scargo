package com.scargo.amr.security;

import com.scargo.amr.service.AmrAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 26.10.22 허브 앱 출입증(X-AMR-Token)을 Spring Security 인증으로 바꾼다.
 *  - 기존에는 AmrAuthConfig 인터셉터가 "ADMIN 세션 또는 출입증"만 확인했고 허브 구분이 없었다
 *  - 이제 출입증은 ROLE_AMR_DEVICE + 허브 범위(AmrDevicePrincipal)로 인증되어 @PreAuthorize와 AmrAccessPolicy가 함께 판단한다
 *  - /api/amr/** 에서만 동작한다 (다른 API에서는 출입증을 로그인으로 인정하지 않음)
 *  - 웹 로그인 세션이 이미 있으면 세션을 우선한다
 */
@Component
@RequiredArgsConstructor
public class AmrTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-AMR-Token";
    public static final String ROLE = "ROLE_AMR_DEVICE";

    private final AmrAuthService authService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/amr/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing == null || existing instanceof AnonymousAuthenticationToken) {
            String raw = request.getHeader(HEADER);
            // 사진은 <img> 태그라 헤더를 못 보내서 ?t= 로 받는다 (기존 동작 유지)
            if ((raw == null || raw.isBlank()) && request.getRequestURI().endsWith("/photo")) {
                raw = request.getParameter("t");
            }
            AmrDevicePrincipal device = raw == null || raw.isBlank() ? null : authService.authenticate(raw);
            if (device != null) {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(new UsernamePasswordAuthenticationToken(
                        device, null, List.of(new SimpleGrantedAuthority(ROLE))));
                SecurityContextHolder.setContext(context);
            }
        }
        chain.doFilter(request, response);
    }
}
