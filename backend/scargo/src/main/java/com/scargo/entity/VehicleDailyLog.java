package com.scargo.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "vehicle_daily_logs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VehicleDailyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "vehicle_no", nullable = false, length = 20)
    private String vehicleNo;

    @Column(name = "driving_date", nullable = false)
    private LocalDate drivingDate;

    @Column(name = "daily_distance", nullable = false)
    private Integer dailyDistance;

    @Column(name = "accumulated_mileage", nullable = false)
    private Integer accumulatedMileage;

    @Column(name = "memo", length = 255)
    private String memo;

    @CreationTimestamp
    @Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP")
    private OffsetDateTime createdAt;

    @Builder
    public VehicleDailyLog(String vehicleNo, LocalDate drivingDate, Integer dailyDistance, Integer accumulatedMileage, String memo) {
        this.vehicleNo = vehicleNo;
        this.drivingDate = drivingDate;
        this.dailyDistance = dailyDistance;
        this.accumulatedMileage = accumulatedMileage;
        this.memo = memo;
    }
}