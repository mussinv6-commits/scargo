package com.scargo.security;

import com.scargo.repository.AccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * AccountController.login() 이 HttpSession 에 accountId 만 저장하고
 * Spring Security 의 SecurityContext 는 채우지 않기 때문에,
 * 컨트롤러에 걸린 @PreAuthorize("hasRole('ADMIN')") 등이 항상
 * "인증되지 않음"으로 판단되어 매 요청마다 403 을 반환하던 문제를 해결한다.
 *
 * 매 요청마다 세션에 accountId 가 있으면 해당 계정을 조회해서
 * userType(Account.UserType enum) 을 ROLE_* 권한으로 변환해 SecurityContext 에 채워 넣는다.
 *
 * 26.10.23 수정: 채운 SecurityContext 를 세션에도 저장한다 (AccountSecurityContext).
 *  저장하지 않으면 SessionManagementFilter 가 세션 ID 를 한 번 더 바꿔서, 로그인 직후 동시에 나간 요청이 401 이 됐다.
 *  로그인 때 이미 저장하므로 보통은 여기까지 오지 않고, 이 수정 전에 만든 세션에서만 쓰인다.
 */
@Component
@RequiredArgsConstructor
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final String SESSION_ACCOUNT_ID = "accountId";

    private final AccountRepository accountRepository;
    private final AccountSecurityContext accountSecurityContext;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        // 익명 인증(anonymousUser)이 먼저 들어가 있으면 로그인 세션이 있어도 권한 검사를 건너뛰게 된다.
        // 기사/사업자 알림 조회가 403으로 막히던 원인.
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing == null || existing instanceof AnonymousAuthenticationToken) {
            HttpSession session = request.getSession(false); // 세션 없으면 새로 만들지 않음
            Long accountId = session == null ? null : toLong(session.getAttribute(SESSION_ACCOUNT_ID));
            if (accountId != null) {
                accountRepository.findById(accountId)
                        .ifPresent(account -> accountSecurityContext.establish(account, request, response));
            }
        }

        filterChain.doFilter(request, response);
    }

    private Long toLong(Object value) {
        if (value instanceof Long id) return id;
        if (value instanceof Integer id) return id.longValue();
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
