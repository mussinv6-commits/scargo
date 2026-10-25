package com.scargo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

// 26.10.23 컨테이너 점검표 변경 이력. 추가만 한다 (DB 트리거 trg_cia_protect 가 수정 · 삭제를 막음)
@Entity
@Table(name = "container_inspection_audits")
@Immutable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContainerInspectionAudit {

    public enum Action { CREATE, UPDATE, FINALIZE, CANCEL, REINSPECT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long auditId;

    @Column(name = "inspection_id", nullable = false)
    private Long inspectionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 20, nullable = false)
    private Action action;

    @Column(name = "actor_account_id")
    private Long actorAccountId;

    @Column(name = "actor_user_id", length = 50)
    private String actorUserId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_data", columnDefinition = "jsonb")
    private Map<String, Object> beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_data", columnDefinition = "jsonb")
    private Map<String, Object> afterData;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
