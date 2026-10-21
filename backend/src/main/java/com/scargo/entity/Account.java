package com.scargo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "user_name", length = 30)
    private String userName;

    @Column(name = "user_id", nullable = false, unique = true, length = 50)
    private String userId;

    @Column(name = "user_pw", nullable = false, length = 255)
    private String userPw;

    // 기본값 없이 계정 생성 시 지정
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 30)
    private UserType userType;

    // 직접 소속된 운영 계열사
    // NULL 허용, ADMIN은 DB CHECK 제약으로 필수
    @Column(name = "operating_company_id")
    private Long operatingCompanyId;

    // 소속 운송사
    @Column(name = "company_id")
    private Long companyId;

    // HUB_OP의 소속 허브
    @Column(name = "hub_id")
    private Long hubId;

    // CENTER_OP의 소속 지역센터
    @Column(name = "center_id")
    private Long centerId;

    @Column(name = "phone_num", length = 20)
    private String phoneNum;

    @Column(name = "business_no", length = 20)
    private String businessNo;

    // DB의 DEFAULT CURRENT_TIMESTAMP 사용
    @Column(
            name = "created_at",
            nullable = false,
            insertable = false,
            updatable = false
    )
    private OffsetDateTime createdAt;

    public enum UserType {
        ADMIN,
        CONTROL,
        HUB_OP,
        CENTER_OP,
        CONTAINER_DRIVER,
        LINEHAUL_DRIVER,
        CORPORATE_APPROVED
    }
}