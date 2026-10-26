package com.scargo.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
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
    private UserType userType = UserType.CONTAINER_DRIVER;

    // 직접 소속된 운영 계열사 (share 26.10.22) — NULL 허용, ADMIN은 DB CHECK 제약으로 필수
    @Column(name = "operating_company_id")
    private Long operatingCompanyId;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "phone_num", length = 20)
    private String phoneNum;

    @Column(name = "business_no", length = 20)
    private String businessNo;

    // 26.10.20 2차 추가(DB 설계 확정): HUB_OP·CENTER_OP 소속 거점 → logistics_centers.center_id
    //  허브와 센터 모두 logistics_centers 행 (허브는 hubs.center_id UNIQUE 로 1:1)
    //  DB CHECK: HUB_OP·CENTER_OP 이면 center_id NOT NULL / center_type 검증은 OpsAccountService
    @Column(name = "center_id")
    private Long centerId;          // CENTER_OP 전용 (지역센터 logistics_centers.center_id)

    // 26.10.21 DB 최종통합: HUB_OP 소속 허브 → hubs.hub_id (chk_accounts_hub_scope)
    @Column(name = "hub_id")
    private Long hubId;

    /** 읽기 전용: 거점 범위 center_id (CENTER_OP=center_id, HUB_OP=소속 허브의 hubs.center_id) */
    @org.hibernate.annotations.Formula("coalesce(center_id, (select h.center_id from hubs h where h.hub_id = hub_id))")
    @Setter(AccessLevel.NONE)
    private Long scopeCenterId;

    // 26.10.21 v10: 기사 가입 승인 (통합 SQL 12-1). 저장은 DB 기본값(APPROVED), 변경은 V9 API(JDBC)
    @Column(name = "approval_status", insertable = false, updatable = false)
    private String approvalStatus;          // PENDING / APPROVED / REJECTED (기사만 PENDING·REJECTED)

    @Column(name = "approved_at", insertable = false, updatable = false)
    private java.time.OffsetDateTime approvedAt;

    @Column(name = "approved_by_account_id", insertable = false, updatable = false)
    private Long approvedByAccountId;

    @Column(name = "reject_reason", insertable = false, updatable = false, columnDefinition = "text")
    private String rejectReason;

    @Column(name = "license_no", length = 30, insertable = false, updatable = false)
    private String licenseNo;

    // DB의 DEFAULT CURRENT_TIMESTAMP 사용
    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    // 통합(26.10.26): scargo_1 7종 + share 회원가입용 GENERAL·CORPORATE_PENDING = DB chk_user_type 9종
    //  간선 → 지선 이름 변경으로 BRANCH_DRIVER 는 BRANCH_DRIVER 로 통일
    public enum UserType {
        ADMIN,              // 총관리자
        CONTROL,            // 통합 관제사
        HUB_OP,             // 허브 관제사 (accounts.hub_id)
        CENTER_OP,          // 지역센터 관제사 (accounts.center_id, DISTRIBUTION_CENTER)
        CONTAINER_DRIVER,   // 컨테이너 운송기사
        BRANCH_DRIVER,    // 지선 운송기사
        CORPORATE_APPROVED, // 운송사 담당자 (회사 is_carrier + approval_status 로 승인 관리)
        // share 26.10.22: 회원가입 화면의 일반회원 · 기업회원(승인 대기). 업무 API 권한은 없고,
        //  관리자가 승인하거나 권한을 바꿔야 업무 화면을 쓸 수 있다
        GENERAL,
        CORPORATE_PENDING
    }
}
