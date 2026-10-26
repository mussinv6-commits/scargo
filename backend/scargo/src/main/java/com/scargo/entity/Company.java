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
    private String businessNo; // 사업자 등록번호 (선택)

    @Column(name = "industry_type", length = 50)
    private String industryType; // 업종 (예: 컨테이너 운송업, 보관업 등)

    @Column(name = "representative_name", length = 50)
    private String representativeName; // 대표자명

    // 26.10.20 2차 추가: 운송사 여부. TRUE 인 회사의 CORPORATE_APPROVED 계정 = 운송사 담당자 (W-07·W-08·W-10)
    @Column(name = "is_carrier", nullable = false)
    private Boolean isCarrier = false;

    // 26.10.20 최종: 운송사 승인은 회사 단위 (is_carrier 일 때만 값이 있다)
    @Column(name = "approval_status", length = 20)
    private String approvalStatus;          // PENDING | APPROVED | REJECTED

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "approved_by_account_id")
    private Long approvedByAccountId;       // FK accounts ON DELETE SET NULL

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

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

    // 26.10.20 운송사 등록·승인 (CHECK: APPROVED 면 approved_at, REJECTED 면 reject_reason 필수)
    public void markCarrierPending() { this.isCarrier = true; if (this.approvalStatus == null) this.approvalStatus = "PENDING"; }
    public void approveCarrier(Long adminAccountId) {
        if (!Boolean.TRUE.equals(isCarrier)) throw new IllegalStateException("운송사가 아닌 업체입니다.");
        this.approvalStatus = "APPROVED"; this.approvedAt = OffsetDateTime.now(); this.approvedByAccountId = adminAccountId; this.rejectReason = null;
    }
    public void rejectCarrier(String reason) {
        if (!Boolean.TRUE.equals(isCarrier)) throw new IllegalStateException("운송사가 아닌 업체입니다.");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("반려 사유를 입력하세요.");
        this.approvalStatus = "REJECTED"; this.rejectReason = reason.trim(); this.approvedAt = null; this.approvedByAccountId = null;
    }

    // fix_1330: 가입 화면에서 새로 만든 업체는 승인 대기 (운송사 여부와 무관). 관리자 승인 전에는 목록·선택에 안 나옴
    public void markSignupPending() { this.approvalStatus = "PENDING"; this.approvedAt = null; this.approvedByAccountId = null; }
    public void approve(Long adminAccountId) {
        this.approvalStatus = "APPROVED"; this.approvedAt = OffsetDateTime.now(); this.approvedByAccountId = adminAccountId; this.rejectReason = null;
    }
    public void reject(String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("반려 사유를 입력하세요.");
        this.approvalStatus = "REJECTED"; this.rejectReason = reason.trim(); this.approvedAt = null; this.approvedByAccountId = null;
    }
    /** 승인 대기·반려 업체는 쓸 수 없음 (approval_status NULL = 승인 절차 없는 기존 화주 업체) */
    public boolean usable() { return approvalStatus == null || "APPROVED".equals(approvalStatus); }

    // 비즈니스 메서드: 정보 수정 로직
    public void update(String companyName, String address, String businessNo, String industryType, String representativeName) {
        if (companyName != null) this.companyName = companyName;
        if (address != null) this.address = address;
        if (businessNo != null) this.businessNo = businessNo;
        if (industryType != null) this.industryType = industryType;
        if (representativeName != null) this.representativeName = representativeName;
    }
}