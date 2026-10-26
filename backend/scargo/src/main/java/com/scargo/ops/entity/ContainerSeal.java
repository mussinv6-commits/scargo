package com.scargo.ops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 11차 container_seals (status: ACTIVE | REMOVED | REPLACED | LOST 그대로).
 *  26.10.20 DB 설계 확정: open_hold, reclass_required 추가. 허브 인수 여부는 seal_events(INSPECTED) 로 판단 */
@Entity @Table(name = "container_seals")
@Getter @Setter @NoArgsConstructor
public class ContainerSeal {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "seal_id") private Long sealId;
    @Column(name = "shipment_id", nullable = false) private Long shipmentId;
    @Column(name = "container_no", length = 11, nullable = false) private String containerNo;
    @Column(name = "seal_no", length = 30, nullable = false) private String sealNo;
    @Column(name = "seal_position", length = 30, nullable = false) private String sealPosition = "MAIN";
    @Column(name = "seal_type", length = 20, nullable = false) private String sealType = "CARRIER";
    @Column(name = "status", length = 20, nullable = false) private String status = "ACTIVE";
    @Column(name = "attached_at", nullable = false) private OffsetDateTime attachedAt = OffsetDateTime.now();
    @Column(name = "attached_by_name", length = 100) private String attachedByName;
    @Column(name = "memo", columnDefinition = "TEXT") private String memo;
    @Column(name = "removed_at") private OffsetDateTime removedAt;   // 11차: ACTIVE 면 NULL, LOST 등이면 필수
    @Column(name = "open_hold", nullable = false) private Boolean openHold = false;
    @Column(name = "reclass_required", nullable = false) private Boolean reclassRequired = false;
}
