package com.scargo.amr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

// 26.10.14 AMR 연동: amr_maintenance (정비 이력 · 예비기 교대)
// status: SCHEDULED(예약) → IN_PROGRESS(정비 중) → DONE
// approved_at / approved_by 는 이번에 추가한 컬럼 (ddl-auto: update 가 자동으로 붙인다)
//   SCHEDULED 이면서 approved_at 이 비어 있으면 "관리자 승인 대기"
@Entity
@Table(name = "amr_maintenance")
@Getter
@Setter
@NoArgsConstructor
public class AmrMaintenance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "maint_id")
    private Long maintId;

    @Column(name = "robot_id", nullable = false)
    private Long robotId;

    @Column(name = "prediction_id")
    private Long predictionId;

    @Column(name = "spare_robot_id")
    private Long spareRobotId;

    @Column(name = "maint_type", length = 20, nullable = false)
    private String maintType = "PLANNED";

    @Column(name = "part_name", length = 50)
    private String partName;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "SCHEDULED";

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    @Column(name = "scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "approved_by", length = 50)
    private String approvedBy; // "앱 · 관리자" / "웹 · 관제" / "자동 (응답 없음)"

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
