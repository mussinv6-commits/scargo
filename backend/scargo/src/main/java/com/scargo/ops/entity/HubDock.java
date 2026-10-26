package com.scargo.ops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;

/** 11차 hub_docks */
@Entity @Table(name = "hub_docks")
@Getter @Setter @NoArgsConstructor
public class HubDock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "dock_id") private Long dockId;
    @Column(name = "hub_id") private Long hubId;
    @Column(name = "dock_code", length = 10) private String dockCode;
    @Column(name = "dock_type", length = 10) private String dockType;     // IN | OUT (11차)
    @Column(name = "region_id") private Long regionId;
    @Column(name = "status", length = 20) private String status;
    @Column(name = "capacity") private Integer capacity;
    @Column(name = "updated_at") private OffsetDateTime updatedAt;
}
