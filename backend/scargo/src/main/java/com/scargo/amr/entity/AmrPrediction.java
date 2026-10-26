package com.scargo.amr.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

// 26.10.14 AMR 연동: amr_predictions (AI 고장 예측 결과)
@Entity
@Table(name = "amr_predictions")
@Getter
@Setter
@NoArgsConstructor
public class AmrPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prediction_id")
    private Long predictionId;

    @Column(name = "robot_id", nullable = false)
    private Long robotId;

    @Column(name = "component", length = 50, nullable = false)
    private String component; // 구동모터 베어링

    @Column(name = "risk_level", length = 10, nullable = false)
    private String riskLevel; // LOW / WARN / CRIT

    @JdbcTypeCode(SqlTypes.NUMERIC) // 26.10.20 DB numeric(7,1)
    @Column(name = "predicted_rul_hours", precision = 7, scale = 1)
    private Double predictedRulHours;

    @JdbcTypeCode(SqlTypes.NUMERIC) // 26.10.20 DB numeric(8,4)
    @Column(name = "vibration_slope", precision = 8, scale = 4)
    private Double vibrationSlope;

    @JdbcTypeCode(SqlTypes.NUMERIC) // 26.10.20 DB numeric(10,1)
    @Column(name = "cum_load_kg", precision = 10, scale = 1)
    private Double cumLoadKg;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "model_version", length = 30)
    private String modelVersion;

    @Column(name = "is_resolved", nullable = false)
    private Boolean resolved = false;

    @Column(name = "predicted_at")
    private OffsetDateTime predictedAt;
}
