package com.scargo.security;

import lombok.Getter;

import java.io.Serializable;

/**
 * SessionAuthenticationFilter가 SecurityContext에 심어주는 인증 주체(principal).
 * 문자열(userId) 하나만 넣어두면 NotificationController 등에서 쓰는
 * "#accountId == principal.id" 같은 SpEL 표현식이 principal.id를 찾지 못해 깨지므로,
 * id/userId/companyId/userType을 프로퍼티로 노출하는 작은 객체로 감싼다.
 */
@Getter
// 26.10.23 세션(SPRING_SECURITY_CONTEXT)에 저장되므로 직렬화 가능하게
public class AuthenticatedAccountPrincipal implements Serializable {

    private static final long serialVersionUID = 1L;


    private final Long id;           // = accountId (principal.id 로 SpEL에서 참조)
    private final String userId;
    private final Long companyId;
    private final String userType;
    private final Long hubId;        // 26.10.22 추가: HUB_OP의 소속 허브 (허브 관제 범위 판정용)

    public AuthenticatedAccountPrincipal(Long id, String userId, Long companyId, String userType) {
        this(id, userId, companyId, userType, null);
    }

    public AuthenticatedAccountPrincipal(Long id, String userId, Long companyId, String userType, Long hubId) {
        this.id = id;
        this.userId = userId;
        this.companyId = companyId;
        this.userType = userType;
        this.hubId = hubId;
    }

    @Override
    public String toString() {
        return userId;
    }
}
