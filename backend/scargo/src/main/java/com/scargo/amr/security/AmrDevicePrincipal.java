package com.scargo.amr.security;

/**
 * 26.10.22 허브 앱(OTP 출입증 X-AMR-Token)으로 들어온 요청의 인증 주체.
 *  - 권한은 ROLE_AMR_DEVICE 하나이고, /api/amr/** 에서만 인정된다
 *  - hubId: 출입증을 만든 OTP 키의 허브. NULL 이면 관리자(ADMIN)가 발급한 전체 허브용 출입증
 */
public record AmrDevicePrincipal(Long keyId, Long hubId, String client) {

    @Override
    public String toString() {
        return "AMR-" + client;
    }
}
