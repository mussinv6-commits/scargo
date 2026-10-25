package com.scargo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 26.10.23 컨테이너 점검표 (팀 16장 container_inspections + 통합본 11장 추가 컬럼)
//  DB 트리거(trg_ci_protect)가 확정 · 취소본 변경, 삭제, 상태 되돌리기를 막는다. 바뀐 컬럼만 UPDATE 하도록 @DynamicUpdate
@Entity
@Table(name = "container_inspections")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContainerInspection {

    // 점검 항목 7개 (모두 필수). true = 양호
    public static final List<String> ITEM_KEYS =
            List.of("front_wall", "left_side", "right_side", "floor", "roof", "doors", "undercarriage");

    public static final String DRAFT = "DRAFT";
    public static final String FINALIZED = "FINALIZED";
    public static final String CANCELLED = "CANCELLED";

    public static final String PENDING = "PENDING";
    public static final String PASS = "PASS";
    public static final String FAIL = "FAIL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inspection_id")
    private Long inspectionId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "driver_account_id")
    private Long driverAccountId;

    @Column(name = "container_no", length = 11)
    private String containerNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "items", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Boolean> items = new LinkedHashMap<>();

    @Column(name = "overall_status", length = 10, nullable = false)
    @Builder.Default
    private String overallStatus = PENDING;

    @Column(name = "memo")
    private String memo;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "hub_id")
    private Long hubId;

    @Column(name = "created_by_account_id")
    private Long createdByAccountId;

    @Column(name = "finalized_by_account_id")
    private Long finalizedByAccountId;

    @Column(name = "cancelled_by_account_id")
    private Long cancelledByAccountId;

    @Column(name = "record_status", length = 20, nullable = false)
    @Builder.Default
    private String recordStatus = DRAFT;

    @Column(name = "finalized_at")
    private OffsetDateTime finalizedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "previous_inspection_id", updatable = false)
    private Long previousInspectionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // DB 기본값 · 트리거(scargo_touch_updated_at)가 채움
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    /** 판정 계산: 7개가 다 들어오기 전 PENDING, 모두 양호 PASS, 하나라도 불량 FAIL */
    public static String judge(Map<String, Boolean> items) {
        if (items == null || !items.keySet().containsAll(ITEM_KEYS)) return PENDING;
        return items.values().stream().allMatch(Boolean.TRUE::equals) ? PASS : FAIL;
    }

    public boolean isDraft() { return DRAFT.equals(recordStatus); }
    public boolean isFinalized() { return FINALIZED.equals(recordStatus); }
}
