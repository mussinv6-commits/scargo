package com.scargo.ops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** v11 반납 CY 목록 (통합 SQL 13-1). v12: is_demo = 데모(가상) CY (시드 4곳 TRUE) */
@Entity @Table(name = "return_cys")
@Getter @Setter @NoArgsConstructor
public class ReturnCy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "cy_id") private Long cyId;
    @Column(name = "cy_code", length = 20, nullable = false, unique = true) private String cyCode;
    @Column(name = "cy_name", length = 100, nullable = false) private String cyName;
    @Column(name = "terminal_id") private Long terminalId;
    @Column(name = "address", columnDefinition = "text") private String address;
    @Column(name = "latitude") private BigDecimal latitude;
    @Column(name = "longitude") private BigDecimal longitude;
    @Column(name = "is_active", nullable = false) private Boolean isActive = true;
    @Column(name = "sort_order") private Integer sortOrder;
    @Column(name = "is_demo", nullable = false) private Boolean isDemo = false;
    @Column(name = "created_at", insertable = false, updatable = false) private OffsetDateTime createdAt;
}
