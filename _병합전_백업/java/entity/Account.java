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

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "user_type", nullable = false, length = 30)
    private UserType userType = UserType.GENERAL;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "phone_num", length = 20)
    private String phoneNum;

    @Column(name = "business_no", length = 20)
    private String businessNo;

    // DB의 DEFAULT CURRENT_TIMESTAMP 사용
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    public enum UserType {
        GENERAL,
        CORPORATE_PENDING,
        CORPORATE_APPROVED,
        ADMIN,
        // 26.10.22 통합 DB 의 구간별 권한 5종 (DB chk_user_type 과 같은 이름). 이 값이 있어도 화면·API 가 깨지지 않게 enum 에만 등록
        CONTROL,            // 통합 관제사
        HUB_OP,             // 허브 관제사
        CENTER_OP,          // 지역센터 관제사
        CONTAINER_DRIVER,   // 컨테이너 운송기사 (항만 → 허브)
        LINEHAUL_DRIVER     // 간선 운송기사 (허브 → 지역센터)
    }
}