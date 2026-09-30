package com.scargo.dto;

import com.scargo.entity.Company;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
public class CompanyResponse {  //조회 및 응답
    private final String businessNo;                 //사업자 등록번호
    private final String companyName;                //업체명
    private final String industryType;               //업종
    private final String representativeName;         //대포명
    private final String address;                    //주소
    private final OffsetDateTime createdAt;          // 등록일

    public CompanyResponse(Company company) {
        this.businessNo = company.getBusinessNo();
        this.companyName = company.getCompanyName();
        this.industryType = company.getIndustryType();
        this.representativeName = company.getRepresentativeName();
        this.address = company.getAddress();
        this.createdAt = company.getCreatedAt();
    }
}