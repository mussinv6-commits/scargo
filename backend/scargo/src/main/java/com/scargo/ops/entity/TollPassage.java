package com.scargo.ops.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 11차 toll_passages. passage_status: PENDING | PASSED | BLOCKED.
 *  과적 = passage_status 'BLOCKED' AND block_reason 'OVERLOAD' (check_id 필수) */
@Entity @Table(name = "toll_passages")
@Getter @Setter @NoArgsConstructor
public class TollPassage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "passage_id") private Long passageId;
    @Column(name = "tollgate_id", nullable = false) private Long tollgateId;
    @Column(name = "recognized_plate_no", length = 20) private String recognizedPlateNo;
    @Column(name = "vehicle_no", length = 20) private String vehicleNo;
    @Column(name = "assignment_id") private Long assignmentId;
    @Column(name = "check_id") private Long checkId;
    @Column(name = "recognition_status", length = 20, nullable = false) private String recognitionStatus;
    @Column(name = "passage_status", length = 20, nullable = false) private String passageStatus = "PENDING";
    @Column(name = "block_reason", length = 30) private String blockReason;
    @Column(name = "detected_at", nullable = false) private OffsetDateTime detectedAt = OffsetDateTime.now();
    @Column(name = "passed_at") private OffsetDateTime passedAt;
}
