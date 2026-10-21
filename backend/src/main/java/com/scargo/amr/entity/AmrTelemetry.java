package com.scargo.amr.entity;

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

    @Column(name = "vibration_mm_s")
    private Double vibration;

    @Column(name = "motor_temp_c")
    private Double motorTemp;

    @Column(name = "battery_pct")
    private Double batteryPct;

    @Column(name = "health_score")
    private Double healthScore;

    @Column(name = "cum_load_kg")
    private Double cumLoadKg;

    @Column(name = "speed_m_s")
    private Double speed;

    @Column(name = "pos_x")
    private Double posX;

    @Column(name = "pos_y")
    private Double posY;

    @Column(name = "state", length = 20)
    private String state;

    @Column(name = "recorded_at")
    private OffsetDateTime recordedAt;
}
