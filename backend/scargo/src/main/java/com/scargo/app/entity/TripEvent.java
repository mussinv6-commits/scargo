package com.scargo.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 26.10.03 2차: 운행 이벤트 (게이트아웃, 영업소 통과, 검문소 접근, 높이 경고, 경로 이탈, HUB 도착, 인계) */
@Entity
@Table(name = "trip_events")
@Getter
@Setter
@NoArgsConstructor
public class TripEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "ref_type", length = 30)
    private String refType;

    @Column(name = "ref_id", length = 50)
    private String refId;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;
}
