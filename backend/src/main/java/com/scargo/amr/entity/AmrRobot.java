package com.scargo.amr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

// 26.10.14 AMR 연동: amr_robots (로봇 마스터 + 현재 상태). 시뮬레이터가 2초마다 현재 값을 덮어쓴다
@Entity
@Table(name = "amr_robots")
@Getter
@Setter
@NoArgsConstructor
public class AmrRobot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "robot_id")
    private Long robotId;

    @Column(name = "hub_id", nullable = false)
    private Long hubId;

    @Column(name = "robot_code", length = 20, nullable = false)
    private String robotCode; // AMR-01 ~ AMR-05, FL-01

    @Column(name = "robot_type", length = 20, nullable = false)
    private String robotType; // AMR / FORKLIFT

    @Column(name = "robot_role", length = 20, nullable = false)
    private String robotRole; // ACTIVE / SPARE

    @Column(name = "state", length = 20, nullable = false)
    private String state; // IDLE, TO_PICK, LOADING, TO_DROP, UNLOADING, TO_MAINT, MAINT, TO_HOME, TO_RACK, FORK_WORK, CHARGING

    @Column(name = "battery_pct")
    private Double batteryPct;

    @Column(name = "health_score")
    private Double healthScore;

    @Column(name = "cum_load_kg", nullable = false)
    private Double cumLoadKg;

    @Column(name = "last_maintained_at")
    private OffsetDateTime lastMaintainedAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
