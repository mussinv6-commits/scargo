package com.scargo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "overload_checks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OverloadCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "check_id")
    private Long checkId;                  // 과적체크 고유 ID, 기본키

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_no", nullable = false)
    private Truck truck;                   // 검사 대상 화물차 (외래키)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "container_no")
    private Container container;           // 검사 대상 컨테이너 (외래키)

    @Column(name = "axle_weight", precision = 8, scale = 2)
    private BigDecimal axleWeight;         // 축중(축별 하중)

    @Column(name = "vgm_weight", precision = 8, scale = 2)
    private BigDecimal vgmWeight;          // VGM(총중량)

    @Column(name = "is_violation", nullable = false)
    private boolean violation;             // 과적 위반 여부

    @Column(name = "violation_reason", columnDefinition = "TEXT")
    private String violationReason;        // 위반 사유

    @Column(name = "retry_count", nullable = false)
    private int retryCount;                // 재검증 횟수

    @Column(name = "is_passed")
    private Boolean passed;                // 최종 통과 여부

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private Account approvedBy;            // 승인한 관리자 계정 (외래키)

    @Column(name = "checked_at", insertable = false, updatable = false)
    private OffsetDateTime checkedAt;      // 검사 시각
}