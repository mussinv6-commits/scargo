package com.scargo.security;

import com.scargo.entity.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 게이트 통과 이력(GateLog) 조회·수정 범위 결정.
 *  - ADMIN                 : 전체 (범위 없음)
 *  - CORPORATE_APPROVED    : 자기 업체 소속 차량의 이력만 (범위 = 소속 업체 ID)
 *  - 그 밖의 역할          : 접근 불가
 */
@Component
@RequiredArgsConstructor
public class GateLogAccessPolicy {

    private final TruckAccessPolicy truckAccessPolicy;

    /** null = 전체(관리자), 값 = 그 업체 소속 차량의 이력만. 접근 권한이 없으면 AccessDeniedException(403) */
    public Long scopeOf(Authentication authentication) {
        Account account = truckAccessPolicy.currentAccount(authentication);
        if (account.getUserType() == Account.UserType.ADMIN) {
            return null;
        }
        if (account.getUserType() == Account.UserType.CORPORATE_APPROVED && account.getCompanyId() != null) {
            return account.getCompanyId();
        }
        throw new AccessDeniedException("게이트 통과 이력은 관리자와 소속 업체 담당자만 볼 수 있습니다.");
    }
}
