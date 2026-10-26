package com.scargo.app.security;

import com.scargo.app.entity.AppToken;
import com.scargo.app.repository.AppTokenRepository;
import com.scargo.entity.Account;
import com.scargo.repository.AccountRepository;
import com.scargo.security.AuthenticatedAccountPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 26.10.03 2차 추가: 기사 앱(APK)용 토큰 인증.
 *
 * APK 안의 화면은 http://localhost 에서 돌고 백엔드는 http://PC-IP:8080 이라 서로 다른 출처(origin)다.
 * 이 경우 세션 쿠키(JSESSIONID)가 잘 전달되지 않으므로, 앱은 로그인 때 받은 토큰을
 * "Authorization: Bearer <토큰>" 헤더로 보낸다.
 * 토큰이 맞으면 SessionAuthenticationFilter 와 똑같은 principal/권한을 SecurityContext 에 넣어서
 * 기존 컨트롤러의 @PreAuthorize 규칙도 그대로 동작한다.
 */
@Component
@RequiredArgsConstructor
public class AppTokenAuthenticationFilter extends OncePerRequestFilter {

    private final AppTokenRepository appTokenRepository;
    private final AccountRepository accountRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            appTokenRepository.findById(token)
                    .filter(t -> t.getExpiresAt().isAfter(OffsetDateTime.now()))
                    .map(AppToken::getAccountId)
                    .flatMap(accountRepository::findById)
                    .ifPresent(this::authenticate);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(Account account) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        com.scargo.ops.CarrierLegacy.roles(account).forEach(r -> authorities.add(new SimpleGrantedAuthority(r))); // 26.10.20 운송사 담당자 호환 권한
        if (account.getCompanyId() != null) {
            authorities.add(new SimpleGrantedAuthority("COMPANY_" + account.getCompanyId()));
        }
        AuthenticatedAccountPrincipal principal = new AuthenticatedAccountPrincipal(
                account.getAccountId(), account.getUserId(), account.getCompanyId(), com.scargo.ops.CarrierLegacy.displayUserType(account));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
        SecurityContextHolder.setContext(context);
    }
}
