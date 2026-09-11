package com.scargo.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(
    name = "companies",
    // 동일명+동일주소 업체 중복등록 방지
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_company_name_address",
            columnNames = {"company_name", "address"}
        )
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "company_id")
    private Long companyId; // 업체 고유 ID (새로운 대리 기본키)

    @Column(name = "company_name", length = 100, nullable = false)
    private String companyName; // 업체명 (필수)

    @Column(name = "address", columnDefinition = "TEXT", nullable = false)
    private String address; // 업체 주소 (업체명과 함께 식별용으로 필수)

    @Column(name = "business_no", length = 20)
    private String businessNo; // 사업자 등록번호 (이제는 일반/선택 컬럼으로 강등됨)

    @Column(name = "industry_type", length = 50)
    private String industryType; // 업종 (예: 컨테이너 운송업, 보관업 등)

    @Column(name = "representative_name", length = 50)
    private String representativeName; // 대표자명

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt; // 등록 일시 (타임존 포함)

    @Builder
    public Company(String companyName, String address, String businessNo, String industryType, String representativeName) {
        this.companyName = companyName;
        this.address = address;
        this.businessNo = businessNo;
        this.industryType = industryType;
        this.representativeName = representativeName;
    }
}