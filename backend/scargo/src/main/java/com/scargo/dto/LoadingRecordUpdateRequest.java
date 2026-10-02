package com.scargo.dto;

import com.scargo.entity.LoadingRecord.LoadingStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoadingRecordUpdateRequest {

    @Size(max = 20, message = "차량 번호는 최대 20자까지 입력할 수 있습니다.")
    private String vehicleNo; // 변경할 화물차 번호 (Truck FK)

    @Pattern(regexp = "^[A-Z]{4}[0-9]{7}$", message = "컨테이너 번호 형식이 올바르지 않습니다. (예: BICU1234567)")
    @Size(max = 11, message = "컨테이너 번호는 최대 11자까지 입력할 수 있습니다.")
    private String containerNo; // 변경할 컨테이너 고유번호 (Container FK)

    private Long locationId; // 변경할 적재 장소 ID (LoadingLocation FK)

    @Pattern(regexp = "^(PENDING|IN_PROGRESS|COMPLETED|CANCELED)$", message = "올바른 작업 상태값이 아닙니다. (PENDING, IN_PROGRESS, COMPLETED, CANCELED 중 선택)")
    private String status; // 변경할 작업 상태 (NULL일 경우 기존 값 유지)

    // Enum 변환 편의 메서드 (서비스 레이어에서 활용)
    public LoadingStatus getStatusAsEnum() {
        if (this.status == null || this.status.isBlank()) {
            return null;
        }
        return LoadingStatus.valueOf(this.status);
    }
}