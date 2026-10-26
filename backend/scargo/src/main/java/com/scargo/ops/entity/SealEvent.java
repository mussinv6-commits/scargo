package com.scargo.ops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 11차 seal_events. number_matches 는 DB 생성 컬럼(읽기 전용).
 *  예외 규칙: number_matches=FALSE → SEAL_MISMATCH, observed_condition DAMAGED → SEAL_DAMAGED, MISSING → SEAL_LOST */
@Entity @Table(name = "seal_events")
@Getter @Setter @NoArgsConstructor
public class SealEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "event_id") private Long eventId;
    @Column(name = "seal_id", nullable = false) private Long sealId;
    @Column(name = "shipment_id", nullable = false) private Long shipmentId;
    @Column(name = "order_id") private Long orderId;
    @Column(name = "event_type", length = 20, nullable = false) private String eventType;  // ATTACHED|INSPECTED|OPENED|REPLACED|LOST|DAMAGED
    @Column(name = "expected_seal_no", length = 30, nullable = false) private String expectedSealNo;
    @Column(name = "observed_seal_no", length = 30) private String observedSealNo;
    @Column(name = "observed_condition", length = 20, nullable = false) private String observedCondition = "UNKNOWN"; // INTACT|DAMAGED|MISSING|UNKNOWN
    @Column(name = "number_matches", insertable = false, updatable = false,
            columnDefinition = "BOOLEAN GENERATED ALWAYS AS (CASE WHEN observed_seal_no IS NULL THEN NULL ELSE observed_seal_no = expected_seal_no END)")
    private Boolean numberMatches;
    @Column(name = "actor_account_id") private Long actorAccountId;
    @Column(name = "actor_name", length = 100) private String actorName;
    @Column(name = "center_id") private Long centerId;
    @Column(name = "occurred_at", nullable = false) private OffsetDateTime occurredAt = OffsetDateTime.now();
    @Column(name = "reason", columnDefinition = "TEXT") private String reason;
    @Column(name = "memo", columnDefinition = "TEXT") private String memo;
}
