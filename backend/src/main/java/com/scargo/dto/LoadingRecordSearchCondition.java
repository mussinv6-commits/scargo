package com.scargo.dto;

import com.scargo.entity.LoadingRecord.LoadingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoadingRecordSearchCondition {

    private String vehicleNo;      // 차량 번호 조건 (동등 또는 포함 검색)
    private String containerNo;    // 컨테이너 번호 조건 (동등 또는 포함 검색)
    private Long locationId;       // 장소 ID 조건
    private LoadingStatus status;  // 작업 상태 조건 (IN_PROGRESS, COMPLETED 등)

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime startDate; // 조회 시작일

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private OffsetDateTime endDate;   // 조회 종료일
}