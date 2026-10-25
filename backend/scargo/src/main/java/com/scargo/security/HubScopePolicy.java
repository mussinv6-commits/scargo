package com.scargo.security;

import com.scargo.entity.Account;
import com.scargo.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 26.10.23 허브 도착 · 인계 업무 범위 (HubArrivalService · ContainerInspectionAccessPolicy 공용)
 *
 *   ADMIN     전체 허브 조회 · 처리
 *   CONTROL   전체 허브 조회만
 *   HUB_OP    소속 허브(accounts.hub_id)만 조회 · 처리
 *   그 외      접근 불가 (CENTER_OP 포함)
 *
 * 소속 허브는 세션 값이 아니라 accounts.hub_id 를 DB 에서 다시 읽어 판정한다 (소속이 바뀌면 바로 반영).
 */
@Component
@RequiredArgsConstructor
public class HubScopePolicy {

    private final AccountRepository accountRepository;

    /** hubId = null 이면 전체 허브 (ADMIN · CONTROL) */
    public record HubScope(Long accountId, String userId, Account.UserType userType, Long hubId, boolean canManage) {
        public boolean allHubs() { return hubId == null; }
        public boolean includes(Long targetHubId) { return allHubs() || hubId.equals(targetHubId); }
    }

    public Account currentAccount(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedAccountPrincipal principal)) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
        return accountRepository.findById(principal.getId())
                .orElseThrow(() -> new AccessDeniedException("유효한 계정이 아닙니다."));
    }

    /** 조회 범위. 허브 업무 역할이 아니면 403 */
    public HubScope view(Authentication authentication) {
        return scopeOf(currentAccount(authentication));
    }

    /** 처리(도착 확정 · 인계) 범위. CONTROL 은 조회만이라 403 */
    public HubScope manage(Authentication authentication) {
        HubScope scope = view(authentication);
        if (!scope.canManage()) {
            throw new AccessDeniedException("허브 도착 · 인계는 조회만 할 수 있습니다.");
        }
        return scope;
    }

    public HubScope scopeOf(Account account) {
        Account.UserType type = account.getUserType();
        if (type == Account.UserType.ADMIN) {
            return new HubScope(account.getAccountId(), account.getUserId(), type, null, true);
        }
        if (type == Account.UserType.CONTROL) {
            return new HubScope(account.getAccountId(), account.getUserId(), type, null, false);
        }
        if (type == Account.UserType.HUB_OP && account.getHubId() != null) {
            return new HubScope(account.getAccountId(), account.getUserId(), type, account.getHubId(), true);
        }
        throw new AccessDeniedException("허브 업무 권한이 없습니다.");
    }

    public void requireHub(HubScope scope, Long hubId) {
        if (!scope.includes(hubId)) {
            throw new AccessDeniedException("소속 허브의 오더만 다룰 수 있습니다.");
        }
    }
}
