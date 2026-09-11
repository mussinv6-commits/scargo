package com.scargo.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CompanyCreateRequest {  //생성용 (Getter)
    private String businessNo;                //사업자 등록 번호
    private String companyName;               // 업체명
    private String industryType;              // 업종명
    private String representativeName;        // 대표명
    private String address;                   // 주소
}