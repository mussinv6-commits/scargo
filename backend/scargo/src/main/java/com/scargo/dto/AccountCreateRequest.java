package com.scargo.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountCreateRequest {

    private String userId;      // 로그인 아이디
    private String userPw;      // 비밀번호
    private String userName;    // 회원명
    private String phoneNum;    // 휴대폰 번호

    // 필수: ADMIN, CONTROL, HUB_OP, CENTER_OP,
    // CONTAINER_DRIVER, LINEHAUL_DRIVER, CORPORATE_APPROVED
    private String userType;

    // 운송사 및 기사 계정의 소속 업체
    private Long companyId;

    // HUB_OP만 필수, 다른 권한은 null
    private Long hubId;

    // CENTER_OP만 필수, 다른 권한은 null
    private Long centerId;

    // 선택 입력: 000-00-00000 형식
    // 기업계정은 입력 시 소속 업체 사업자번호와 일치해야 함
    private String businessNo;

    // 업체 선택 화면에서 사용하는 보조 정보
    private String companyName;
    private String address;
}