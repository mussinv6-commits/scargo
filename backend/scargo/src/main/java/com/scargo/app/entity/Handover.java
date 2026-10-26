package com.scargo.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/** 26.10.03 2차: HUB 인계 기록 */
@Entity
@Table(name = "handovers")
@Getter
@Setter
@NoArgsConstructor
public class Handover {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "handover_id")
    private Long handoverId;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "container_no_confirmed", nullable = false)
    private Boolean containerNoConfirmed = false;

    @Column(name = "seal_no", length = 30)
    private String sealNo;

    // fix_1300: 인계 시 대조한 정답 씰 (둘 다 NULL 또는 둘 다 입력: fk_handover_seal MATCH FULL)
    @Column(name = "seal_id")
    private Long sealId;
    @Column(name = "seal_shipment_id")
    private Long sealShipmentId;

    @Column(name = "seal_ok", nullable = false)
    private Boolean sealOk = false;

    @Column(name = "damage_free", nullable = false)
    private Boolean damageFree = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "photo_urls", columnDefinition = "jsonb")
    private String photoUrls;

    @Column(name = "receiver_name", length = 50)
    private String receiverName;

    @Column(name = "signature_url", columnDefinition = "TEXT")
    private String signatureUrl;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
