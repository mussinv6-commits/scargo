package com.scargo.security;

import lombok.Getter;

/**
 * SessionAuthenticationFilter가 SecurityContext에 심어주는 인증 주체(principal).
 * 문자열(userId) 하나만 넣어두면 NotificationController 등에서 쓰는
 * "#accountId == principal.id" 같은 SpEL 표현식이 principal.id를 찾지 못해 깨지므로,
 * id/userId/companyId/userType을 프로퍼티로 노출하는 작은 객체로 감싼다.
 */
@Getter
public class AuthenticatedAccountPrincipal {

    private final Long id;           // = accountId (principal.id 로 SpEL에서 참조)
    private final String userId;
    private final Long companyId;
    private final String userType;

    public AuthenticatedAccountPrincipal(Long id, String userId, Long companyId, String userType) {
        this.id = id;
        this.userId = userId;
        this.companyId = companyId;
        this.userType = userType;
    }

    @Override
    public String toString() {
        return userId;
    }
}
