package com.scargo.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyUpdateRequest {

    @Size(max = 100, message = "업체명은 최대 100자까지 입력 가능합니다.")
    private String companyName;

    private String address; // TEXT 타입 매핑 (업체명과 함께 유니크 제약조건 대상)

    @Size(max = 20, message = "사업자 등록번호는 최대 20자까지 입력 가능합니다.")
    private String businessNo;

    @Size(max = 50, message = "업종은 최대 50자까지 입력 가능합니다.")
    private String industryType;

    @Size(max = 50, message = "대표자명은 최대 50자까지 입력 가능합니다.")
    private String representativeName;
}