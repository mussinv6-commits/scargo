package com.scargo.ops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 2차 exceptions (DB 설계 확정 26.10.20). 원본: seal_events / toll_passages / parcels / items.
 *  CHECK: exception_type, severity, status, source_table / RESOLVED 면 resolved_at·resolution_note 필수
 *  UNIQUE(source_table, source_key, exception_type) / INDEX(status, center_id, occurred_at DESC) */
@Entity
@Table(name = "exceptions",
       uniqueConstraints = @UniqueConstraint(name = "uk_exceptions_source", columnNames = {"source_table", "source_key", "exception_type"}),
       indexes = @Index(name = "idx_exceptions_status_center_time", columnList = "status, center_id, occurred_at DESC"))
@Getter @Setter @NoArgsConstructor
public class OpsException {
    public static final java.util.Set<String> SOURCE_TABLES = java.util.Set.of("seal_events", "toll_passages", "parcels", "items", "branch_units", "transport_orders", "return_plan_changes", "empty_container_inspections"); // v11: 반납 기한
    public static final java.util.Set<String> TYPES = java.util.Set.of("SEAL_MISMATCH", "SEAL_DAMAGED", "SEAL_LOST", "OVERLOAD", "SORT_FAILED", "ITEM_DAMAGED", "OTHER", "CONTAINER_RETURN_DUE", "RETURN_CY_CHANGED", "CONTAINER_DAMAGED", "CONTAINER_RETURN_OVERDUE", "SEAL_UNREGISTERED"); // v11 · fix_1300: 반납 기한 초과, 정답 씰 미등록
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "exception_id") private Long exceptionId;
    @Column(name = "exception_type", length = 30, nullable = false) private String exceptionType;
    @Column(name = "severity", length = 10, nullable = false) private String severity;     // CRITICAL | WARNING
    @Column(name = "center_id") private Long centerId;                                     // logistics_centers. 도로 위(과적)는 NULL
    @Column(name = "assignment_id") private Long assignmentId;                             // shipment_legs (도로 위 연결)
    @Column(name = "message", columnDefinition = "TEXT", nullable = false) private String message;
    @Column(name = "source_table", length = 30) private String sourceTable;                // seal_events | toll_passages | parcels | items | branch_units
    // 26.10.20 최종: source_id BIGINT → source_key VARCHAR(50) (parcels.parcel_no·branch_units.unit_id 가 문자열 PK)
    @Column(name = "source_key", length = 50) private String sourceKey;
    @Column(name = "status", length = 20, nullable = false) private String status = "OPEN"; // OPEN → IN_PROGRESS → RESOLVED
    @Column(name = "assignee_account_id") private Long assigneeAccountId;                  // FK accounts
    @Column(name = "resolution_note", columnDefinition = "TEXT") private String resolutionNote;
    @Column(name = "occurred_at", nullable = false) private OffsetDateTime occurredAt;
    @Column(name = "resolved_at") private OffsetDateTime resolvedAt;
}
