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
    
    // 권한 및 상태 구분 (GENERAL: 일반회원, CORPORATE_PENDING: 기업회원(대기))
    private String userType;    // 가입 유형 또는 권한 구분 값 (선택적 또는 서비스 내에서 자동 할당)

    // 기업회원 관련 정보
    private Long companyId;     // 소속 업체 고유 ID (companies 테이블 기본키, 추천 방식)
    private String businessNo;  // 기업회원 가입 시 입력하는 사업자 등록번호

    private String companyName; // 업체명 (소속 회사 선택용)
    private String address;     // 업체 주소 (동일 업체명 구분을 위한 식별용)
}