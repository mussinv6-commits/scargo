package com.scargo.ops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

/** v12 (통합 SQL 14-5): 공컨 반납 선택 첨부 — EIR_IN(CY EIR IN) / DAMAGE_CONFIRM(손상 확인서).
 *  올리는 사람 = 오더 운송사 CORPORATE_APPROVED 또는 오더 반납 기사 (DB 트리거 trg_return_attachment_validate, 위반 23514 → 409) */
@Entity @Table(name = "return_attachments")
@Getter @Setter @NoArgsConstructor
public class ReturnAttachment {
    public static final java.util.Set<String> KINDS = java.util.Set.of("EIR_IN", "DAMAGE_CONFIRM");
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "attachment_id") private Long attachmentId;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "kind", length = 20, nullable = false) private String kind;
    @Column(name = "file_url", columnDefinition = "text", nullable = false) private String fileUrl;
    @Column(name = "uploaded_by_account_id", nullable = false) private Long uploadedByAccountId;
    @Column(name = "uploaded_at", insertable = false, updatable = false) private OffsetDateTime uploadedAt;
}
