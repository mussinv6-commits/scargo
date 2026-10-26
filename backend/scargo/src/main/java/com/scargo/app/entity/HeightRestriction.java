package com.scargo.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 26.10.03 2차: 높이제한 (TERMINAL_GATE / HUB_ENTRANCE / ROAD) */
@Entity
@Table(name = "height_restrictions")
@Getter
@Setter
@NoArgsConstructor
public class HeightRestriction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "restriction_id")
    private Long restrictionId;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "terminal_id")
    private Long terminalId;

    @Column(name = "gate_id")
    private Long gateId;

    @Column(name = "hub_id")
    private Long hubId;

    @Column(name = "restriction_name", nullable = false, length = 150)
    private String restrictionName;

    @Column(name = "limit_height_m", nullable = false, precision = 4, scale = 2)
    private BigDecimal limitHeightM;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "alert_radius_m", nullable = false)
    private Integer alertRadiusM = 1000;

    @Column(name = "source", length = 100)
    private String source;

    @Column(name = "verified_at")
    private LocalDate verifiedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
