package com.scargo.security;

import com.scargo.entity.Account;
import com.scargo.repository.AccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * AccountController.login() 이 HttpSession 에 accountId 만 저장하고
 * Spring Security 의 SecurityContext 는 채우지 않기 때문에,
 * 컨트롤러에 걸린 @PreAuthorize("hasRole('ADMIN')") 등이 항상
 * "인증되지 않음"으로 판단되어 매 요청마다 403 을 반환하던 문제를 해결한다.
 *
 * 매 요청마다 세션에 accountId 가 있으면 해당 계정을 조회해서
 * userType(Account.UserType enum) 을 ROLE_* 권한으로 변환해 SecurityContext 에 채워 넣는다.
 */
@Component
@RequiredArgsConstructor
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private static final String SESSION_ACCOUNT_ID = "accountId";

    private final AccountRepository accountRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            HttpSession session = request.getSession(false); // 세션 없으면 새로 만들지 않음
            if (session != null) {
                Object accountIdAttr = session.getAttribute(SESSION_ACCOUNT_ID);
                if (accountIdAttr instanceof Long accountId) {
                    accountRepository.findById(accountId).ifPresent(this::authenticate);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(Account account) {
        // userType(Enum: ADMIN / GENERAL / CORPORATE_PENDING / CORPORATE_APPROVED)의
        // 이름(name())을 그대로 ROLE_* 권한으로 매핑한다. 기존 컨트롤러들의
        // hasRole('ADMIN'), hasAnyRole('CORPORATE_APPROVED', 'ADMIN') 표기와 그대로 맞는다.
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + account.getUserType().name()));

        // NotificationController 등에서 쓰는 hasAuthority('COMPANY_' + #companyId) 표현식을
        // 만족시키기 위해 소속 회사가 있는 계정에는 COMPANY_<companyId> 권한도 함께 부여한다.
        if (account.getCompanyId() != null) {
            authorities.add(new SimpleGrantedAuthority("COMPANY_" + account.getCompanyId()));
        }

        // principal을 단순 문자열이 아니라 id(getId())를 가진 객체로 바꿔서
        // "#accountId == principal.id" 같은 SpEL 표현식이 정상 동작하도록 함.
        AuthenticatedAccountPrincipal principal = new AuthenticatedAccountPrincipal(
                account.getAccountId(), account.getUserId(), account.getCompanyId(), account.getUserType().name());

        var authToken = new UsernamePasswordAuthenticationToken(
                principal, null, authorities);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authToken);
        SecurityContextHolder.setContext(context);
    }
}
