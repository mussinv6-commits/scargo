package com.scargo.dto;

import com.scargo.entity.LoadingRecord.LoadingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class LoadingRecordCreateRequest {

    @NotBlank(message = "차량 번호는 필수 입력 항목입니다.")
    @Size(max = 20, message = "차량 번호는 최대 20자까지 입력할 수 있습니다.")
    private String vehicleNo; // 적재한 화물차 번호 (Truck FK)

    @NotBlank(message = "컨테이너 번호는 필수 입력 항목입니다.")
    @Pattern(regexp = "^[A-Z]{4}[0-9]{7}$", message = "컨테이너 번호 형식이 올바르지 않습니다. (예: BICU1234567)")
    @Size(max = 11, message = "컨테이너 번호는 최대 11자까지 입력할 수 있습니다.")
    private String containerNo; // 적재된 컨테이너 고유번호 (Container FK)

    @NotNull(message = "적재 장소 ID는 필수 입력 항목입니다.")
    private Long locationId; // 적재가 이루어진 장소 ID (LoadingLocation FK)

    @Pattern(regexp = "^(IN_PROGRESS|COMPLETED|CANCELED)$", message = "올바른 작업 상태값이 아닙니다. (IN_PROGRESS, COMPLETED, CANCELED 중 선택)")
    private String status; // 작업 상태 (미입력 시 기본값 처리)

    // Enum 변환 편의 메서드 (서비스 레이어에서 활용)
    public LoadingStatus getStatusAsEnum() {
        if (this.status == null || this.status.isBlank()) {
            return LoadingStatus.IN_PROGRESS; // 기본값
        }
        return LoadingStatus.valueOf(this.status);
    }
}