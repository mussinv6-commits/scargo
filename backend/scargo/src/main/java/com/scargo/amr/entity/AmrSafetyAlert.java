package com.scargo.amr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 26.11.01 S-Bot AMR 알림 내역 */
@Entity
@Table(name = "amr_safety_alerts")
@Getter
@Setter
@NoArgsConstructor
public class AmrSafetyAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alert_id")
    private Long alertId;

    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType; // FIRE / BATT / ESTOP / TEMP

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "equipment", length = 120)
    private String equipment;

    @Column(name = "zone_label", length = 120)
    private String zoneLabel;

    @Column(name = "kind", length = 20)
    private String kind;

    @Column(name = "code", length = 60)
    private String code;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (startedAt == null) startedAt = now;
        if (createdAt == null) createdAt = now;
    }
}
