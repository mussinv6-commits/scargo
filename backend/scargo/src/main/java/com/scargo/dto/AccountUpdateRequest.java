package com.scargo.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountUpdateRequest {
	private String userId;      // 변경할 ID
    private String userPw;      // 변경할 비밀번호 (입력하지 않으면 기존 유지 등의 로직 처리 가능)
    private String userName;    // 변경할 회원명
    private String phoneNum;    // 변경할 휴대폰 번호

    // 필요에 따라 수정 가능한 기업 정보나 주소 등이 있다면 추가
    private Long companyId;     
    private String companyName; 
    private String address;     
}