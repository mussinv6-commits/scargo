package com.scargo.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 26.10.03 2차: 항만 터미널 (출발지) */
@Entity
@Table(name = "terminals")
@Getter
@Setter
@NoArgsConstructor
public class Terminal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "terminal_id")
    private Long terminalId;

    @Column(name = "terminal_code", nullable = false, unique = true, length = 20)
    private String terminalCode;

    @Column(name = "terminal_name", nullable = false, length = 100)
    private String terminalName;

    @Column(name = "port_name", nullable = false, length = 50)
    private String portName = "부산항";

    @Column(name = "operator", length = 100)
    private String operator;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "coord_verified", nullable = false)
    private Boolean coordVerified = false;   // false = 지도 표시용 대략 좌표

    @Column(name = "gate_height_note", columnDefinition = "TEXT")
    private String gateHeightNote;           // 게이트 높이 확인 상태 메모

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
