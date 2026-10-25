package com.scargo.amr.security;

import com.scargo.amr.entity.Hub;
import com.scargo.amr.repository.HubRepository;
import com.scargo.security.AuthenticatedAccountPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 26.10.22 허브 관제(AMR) 접근 범위
 *
 *   ADMIN     전체 허브 조회 · 관리
 *   CONTROL   전체 허브 조회만
 *   HUB_OP    소속 허브(accounts.hub_id)만 조회 · 관리
 *   허브 앱    출입증을 만든 OTP 키의 허브만 조회 · 관리 (관리자가 만든 키는 전체)
 *   그 외      접근 불가 (CENTER_OP 포함)
 *
 * 컨트롤러의 @PreAuthorize는 역할만 거르고, 어느 허브인지는 서비스가 이 클래스로 확인한다.
 */
@Component
@RequiredArgsConstructor
public class AmrAccessPolicy {

    // 허브를 지정하지 않은 전체 범위 사용자(ADMIN · CONTROL)가 보는 기본 허브 (곤지암허브, 팀 11차 DB hub_id 1)
    public static final String DEFAULT_HUB_CODE = "HUB-GONJIAM-01";

    public enum Access { VIEW, MANAGE }

    private record Scope(Long ownHubId, boolean canManage) {
        boolean allHubs() { return ownHubId == null; }
    }

    private final HubRepository hubRepo;

    /**
     * 요청한 허브를 확인해 돌려준다. hubId를 안 보내면 HUB_OP · 허브 앱은 소속 허브, ADMIN · CONTROL은 기본 허브.
     * 범위 밖 허브면 403, 없는 허브면 404.
     */
    public Long hub(Long requestedHubId, Access access) {
        Scope scope = scope(access);
        if (requestedHubId == null) {
            return scope.allHubs() ? defaultHubId() : scope.ownHubId();
        }
        requireInScope(scope, requestedHubId);
        if (!hubRepo.existsById(requestedHubId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "허브 없음: " + requestedHubId);
        }
        return requestedHubId;
    }

    /** 이미 있는 기록(로봇 · 정비 · 입출차 기록)이 속한 허브를 다룰 수 있는지 확인 */
    public void requireHub(Long resourceHubId, Access access) {
        requireInScope(scope(access), resourceHubId);
    }

    /** 볼 수 있는 허브: null = 전체(ADMIN · CONTROL · 관리자 출입증), 아니면 그 허브 하나 */
    public Long visibleHubId() {
        return currentScope().ownHubId();
    }

    /** 변경 권한이 있는지 (조회 권한이 없으면 403) */
    public boolean canManage() {
        return currentScope().canManage();
    }

    /** 앱 연결 코드(OTP) 발급 범위: ADMIN → null(전체 허브), HUB_OP → 소속 허브. 그 외(허브 앱 포함)는 발급 불가 */
    public Long issuerHubId() {
        Object principal = principal();
        if (principal instanceof AuthenticatedAccountPrincipal p) {
            if ("ADMIN".equals(p.getUserType())) return null;
            if ("HUB_OP".equals(p.getUserType()) && p.getHubId() != null) return p.getHubId();
        }
        throw new AccessDeniedException("웹에서 관리자(ADMIN) 또는 허브 관제사(HUB_OP)로 로그인해야 앱 연결 코드를 다룰 수 있습니다");
    }

    /** 승인 · 설정 변경 기록에 남길 처리자 이름 */
    public String actorName() {
        Object principal = principal();
        if (principal instanceof AuthenticatedAccountPrincipal p) return p.getUserId();
        if (principal instanceof AmrDevicePrincipal) return "허브 앱";
        return null;
    }

    public Long defaultHubId() {
        return hubRepo.findByHubCode(DEFAULT_HUB_CODE).map(Hub::getHubId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "허브 기본 데이터가 없습니다. 최종 DB의 허브(" + DEFAULT_HUB_CODE + ")를 먼저 등록하세요."));
    }

    private void requireInScope(Scope scope, Long hubId) {
        if (!scope.allHubs() && !scope.ownHubId().equals(hubId)) {
            throw new AccessDeniedException("소속 허브만 다룰 수 있습니다 (요청 허브 " + hubId + ")");
        }
    }

    private Scope scope(Access access) {
        Scope scope = currentScope();
        if (access == Access.MANAGE && !scope.canManage()) {
            throw new AccessDeniedException("허브 관제는 조회만 할 수 있습니다");
        }
        return scope;
    }

    private Scope currentScope() {
        Object principal = principal();
        if (principal instanceof AuthenticatedAccountPrincipal p) {
            String type = p.getUserType();
            if ("ADMIN".equals(type)) return new Scope(null, true);
            if ("CONTROL".equals(type)) return new Scope(null, false);
            if ("HUB_OP".equals(type) && p.getHubId() != null) return new Scope(p.getHubId(), true);
        } else if (principal instanceof AmrDevicePrincipal d) {
            return new Scope(d.hubId(), true);
        }
        throw new AccessDeniedException("허브 관제 권한이 없습니다");
    }

    private static Object principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("로그인이 필요합니다");
        }
        return auth.getPrincipal();
    }
}
