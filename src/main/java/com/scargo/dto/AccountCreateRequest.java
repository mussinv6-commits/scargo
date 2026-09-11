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
    private String companyName; // 업체명 (소속 회사 선택용)
    private String address;     // 업체 주소 (동일 업체명 구분을 위한 식별용)
}