package com.scargo.dto;

import com.scargo.entity.Account;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.Locale;

@Getter
public class AccountResponse {

    private Long accountId;
    private String userId;
    private String userName;
    private String userType;

    // 직접 소속된 운영 계열사
    private Long operatingCompanyId;

    private Long companyId;
    private Long hubId;
    private Long centerId;
    private String phoneNum;
    private String businessNo;
    private OffsetDateTime createdAt;

    // ADMIN만 사용: SCARGO 또는 SBOT
    private String adminScope;

    public AccountResponse(Account account) {
        this.accountId = account.getAccountId();
        this.userId = account.getUserId();
        this.userName = account.getUserName();
        this.userType = account.getUserType() != null
                ? account.getUserType().name()
                : null;

        this.operatingCompanyId = account.getOperatingCompanyId();
        this.companyId = account.getCompanyId();
        this.hubId = account.getHubId();
        this.centerId = account.getCenterId();
        this.phoneNum = account.getPhoneNum();
        this.businessNo = account.getBusinessNo();
        this.createdAt = account.getCreatedAt();

        // 기존 관리자 구분 규칙 유지
        if (account.getUserType() == Account.UserType.ADMIN) {
            boolean sbotAdmin = account.getUserId() != null
                    && account.getUserId()
                            .toLowerCase(Locale.ROOT)
                            .startsWith("sbot");

            this.adminScope = sbotAdmin ? "SBOT" : "SCARGO";
        }
    }
}