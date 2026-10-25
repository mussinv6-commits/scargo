package com.scargo.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountUpdateRequest {

    // null 또는 공백이면 기존 값 유지
    private String userPw;
    private String userName;
    private String phoneNum;

    // null 또는 생략하면 기존 소속 유지
    private Long companyId;
    private Long hubId;
    private Long centerId;

    // null이면 기존 값 유지, 공백이면 삭제
    private String businessNo;
}
