package com.scargo.security;

import com.scargo.entity.Account;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 26.10.23 계정 → Spring Security 인증 정보를 만들고 세션에 저장한다 (로그인 · SessionAuthenticationFilter 공용)
 *
 * 로그인 직후 동시 요청 401 원인
 *  - 로그인은 세션에 accountId 만 넣고 SecurityContext 는 세션에 저장하지 않았다
 *  - 그래서 로그인 뒤 첫 요청에서 SessionManagementFilter 가 "이 요청에서 새로 로그인했다"고 보고
 *    세션 고정 공격 방지(ChangeSessionIdAuthenticationStrategy)로 세션 ID 를 한 번 더 바꿨다
 *  - 그 사이 옛 세션 ID 로 출발한 다른 요청은 세션을 못 찾아 401 → 관리자 화면은 401 이면 로그아웃 처리
 * 해결: 로그인할 때(그리고 accountId 로 인증할 때) SecurityContext 를 세션에 저장한다.
 *  - 세션 ID 는 로그인할 때 AccountController 의 changeSessionId() 로 한 번만 바뀐다 (세션 고정 방지는 그대로)
 *  - 권한 · principal 구성은 기존 SessionAuthenticationFilter 와 같다
 */
@Component
public class AccountSecurityContext {

    // Spring Security 기본 저장소와 같은 세션 키(SPRING_SECURITY_CONTEXT)를 쓴다
    private final SecurityContextRepository repository = new HttpSessionSecurityContextRepository();

    /** 기존 권한 규칙 그대로: ROLE_<userType> + 소속 업체가 있으면 COMPANY_<companyId> */
    public static Authentication authenticationOf(Account account) {
        // userType 이름(name())을 그대로 ROLE_* 권한으로 매핑한다.
        // 기존 컨트롤러들의 hasRole('ADMIN'), hasAnyRole('CORPORATE_APPROVED', 'ADMIN') 표기와 그대로 맞는다.
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
                account.getAccountId(), account.getUserId(), account.getCompanyId(), account.getUserType().name(),
                account.getHubId());

        return new UsernamePasswordAuthenticationToken(principal, null, authorities);
    }

    /** 현재 요청을 이 계정으로 인증하고, 같은 인증 정보를 세션에 저장한다 */
    public void establish(Account account, HttpServletRequest request, HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authenticationOf(account));
        SecurityContextHolder.setContext(context);
        repository.saveContext(context, request, response);
    }
}
