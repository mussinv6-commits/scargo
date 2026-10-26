package com.scargo.amr.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

// 26.10.14 AMR 연동: amr_telemetry (센서 시계열)
@Entity
@Table(name = "amr_telemetry")
@Getter
@Setter
@NoArgsConstructor
public class AmrTelemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "telemetry_id")
    private Long telemetryId;

    @Column(name = "robot_id", nullable = false)
    private Long robotId;

    @JdbcTypeCode(SqlTypes.NUMERIC) // 26.10.20 DB numeric(5,2)
    @Column(name = "vibration_mm_s", precision = 5, scale = 2)
    private Double vibration;

    @JdbcTypeCode(SqlTypes.NUMERIC) // 26.10.20 DB numeric(5,2)
    @Column(name = "motor_temp_c", precision = 5, scale = 2)
    private Double motorTemp;

    @JdbcTypeCode(SqlTypes.NUMERIC) // 26.10.20 DB numeric(5,2)
    @Column(name = "battery_pct", precision = 5, scale = 2)
    private Double batteryPct;

    @JdbcTypeCode(SqlTypes.NUMERIC) // 26.10.20 DB numeric(5,2)
    @Column(name = "health_score", precision = 5, scale = 2)
    private Double healthScore;

    @JdbcTypeCode(SqlTypes.NUMERIC) // 26.10.20 DB numeric(10,1)
    @Column(name = "cum_load_kg", precision = 10, scale = 1)
    private Double cumLoadKg;

    @JdbcTypeCode(SqlTypes.NUMERIC) // 26.10.20 DB numeric(5,2)
    @Column(name = "speed_m_s", precision = 5, scale = 2)
    private Double speed;

    @JdbcTypeCode(SqlTypes.NUMERIC) // 26.10.20 DB numeric(7,2)
    @Column(name = "pos_x", precision = 7, scale = 2)
    private Double posX;

    @JdbcTypeCode(SqlTypes.NUMERIC) // 26.10.20 DB numeric(7,2)
    @Column(name = "pos_y", precision = 7, scale = 2)
    private Double posY;

    @Column(name = "state", length = 20)
    private String state;

    @Column(name = "recorded_at")
    private OffsetDateTime recordedAt;
}
