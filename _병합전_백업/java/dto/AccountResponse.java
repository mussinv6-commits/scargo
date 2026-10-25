package com.scargo.dto;

import com.scargo.entity.Account;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
public class AccountResponse {

    private Long accountId;
    private String userId;
    private String userName;
    private String userType; // 응답용이므로 String 유지
    private Long companyId;
    private String phoneNum;
    private String businessNo; 
    private OffsetDateTime createdAt;
    private String adminScope; // 26.10.20 추가: 관리자 구분 (SCARGO = S Cargo 관리자 / SBOT = S-Bot 관리자, 관리자가 아니면 null)

    public AccountResponse(Account account) {
        this.accountId = account.getAccountId();
        this.userId = account.getUserId();
        this.userName = account.getUserName();
        this.userType = account.getUserType() != null ? account.getUserType().name() : null;
        this.companyId = account.getCompanyId();
        this.phoneNum = account.getPhoneNum();
        this.businessNo = account.getBusinessNo(); 
        this.createdAt = account.getCreatedAt();
        // S-Bot 관리자 계정은 아이디가 sbot 으로 시작한다 (AdminInitializer 에서 생성)
        if (account.getUserType() == Account.UserType.ADMIN) {
            this.adminScope = account.getUserId() != null && account.getUserId().toLowerCase().startsWith("sbot") ? "SBOT" : "SCARGO";
        }
    }
}