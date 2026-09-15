package com.scargo.dto;

import com.scargo.entity.Company;
import lombok.Getter;

@Getter
public class CompanyOptionResponse {
    private Long companyId;
    private String companyName;
    private String address;

    public CompanyOptionResponse(Company company) {
        this.companyId = company.getCompanyId();
        this.companyName = company.getCompanyName();
        this.address = company.getAddress();
    }
}