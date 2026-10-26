package com.scargo.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 26.10.03 2차: 운행 GPS 궤적 */
@Entity
@Table(name = "trip_locations")
@Getter
@Setter
@NoArgsConstructor
public class TripLocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "speed_kmh", precision = 5, scale = 1)
    private BigDecimal speedKmh;

    @Column(name = "heading")
    private Short heading;

    @Column(name = "accuracy_m", precision = 6, scale = 1)
    private BigDecimal accuracyM;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;
}
