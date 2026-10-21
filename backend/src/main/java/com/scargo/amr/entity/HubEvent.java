package com.scargo.amr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

// 26.10.14 AMR 연동: hub_events (관제 이벤트 로그 · 화면 알림)
@Entity
@Table(name = "hub_events")
@Getter
@Setter
@NoArgsConstructor
public class HubEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "hub_id", nullable = false)
    private Long hubId;

    @Column(name = "robot_id")
    private Long robotId;

    @Column(name = "event_level", length = 10, nullable = false)
    private String eventLevel; // INFO / OK / WARN / CRIT / DISPATCH

    @Column(name = "phase_no")
    private Integer phaseNo;

    @Column(name = "message", columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
