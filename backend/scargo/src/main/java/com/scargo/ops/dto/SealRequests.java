package com.scargo.ops.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 26.10.21 씰번호 필수 (앱 → 서버). 앱에서 막아도 서버가 한 번 더 막는다 (빈 값·형식 오류 → 400).
 * 형식: 영문 대문자·숫자·하이픈 4~20자 (예: SC-1001, ML-772018, KR-LH-208431). 앞뒤 공백 제거·대문자로 바꿔 저장.
 */
public final class SealRequests {
    private SealRequests() {}

    public static final String SEAL_REGEX = "^[A-Za-z0-9][A-Za-z0-9-]{3,19}$";
    public static final String SEAL_MSG = "씰번호 형식이 올바르지 않습니다 (영문·숫자·하이픈 4~20자, 예: SC-1001)";

    public static String normalize(String s) { return s == null ? null : s.trim().toUpperCase(); }

    /** T-02 컨테이너 인수 */
    public record PickupRequest(
            String containerNo, // fix_1517: 선택 (비우면 서버가 오더의 컨테이너 번호 사용)
            @NotBlank(message = "씰번호를 입력하세요") @Pattern(regexp = SEAL_REGEX, message = SEAL_MSG) String sealNo,
            Boolean checksOk) {}

    /** 지선 출발 국내 봉인 (shipment_legs.domestic_seal_no) */
    public record DepartRequest(
            @NotBlank(message = "차량 문 씰번호를 입력하세요") @Pattern(regexp = SEAL_REGEX, message = SEAL_MSG) String sealNo) {}

    /** 허브 씰 검사. 씰이 아예 없는 경우(MISSING)만 번호 없이 기록 가능 */
    public record HubReceiveRequest(
            String observedSealNo,
            @NotBlank(message = "씰 상태를 선택하세요") @Pattern(regexp = "INTACT|DAMAGED|MISSING", message = "씰 상태는 INTACT·DAMAGED·MISSING 중 하나입니다") String observedCondition) {
        @AssertTrue(message = "현장 씰번호를 입력하세요 (씰이 없으면 상태를 '없음(MISSING)'으로 선택)")
        public boolean isSealPresentUnlessMissing() {
            return "MISSING".equals(observedCondition) || (observedSealNo != null && !observedSealNo.isBlank());
        }
        @AssertTrue(message = SEAL_MSG)
        public boolean isSealFormatOk() {
            return observedSealNo == null || observedSealNo.isBlank() || observedSealNo.trim().matches(SEAL_REGEX);
        }
    }
}
