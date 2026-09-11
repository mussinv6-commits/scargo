package com.scargo.dto;

import com.scargo.entity.Account;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
public class AccountResponse {

    private Long accountId;
    private String userId;
    private String userName;
    private String userType;
    private Long companyId;
    private String phoneNum;
    private OffsetDateTime createdAt;

    public AccountResponse(Account account) {
        this.accountId = account.getAccountId();
        this.userId = account.getUserId();
        this.userName = account.getUserName();
        this.userType = account.getUserType();
        this.companyId = account.getCompanyId();
        this.phoneNum = account.getPhoneNum();
        this.createdAt = account.getCreatedAt();
    }
}