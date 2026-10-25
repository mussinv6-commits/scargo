package com.scargo.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

// 26.10.23 허브 인계 확정 요청
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HubHandoverRequest {

    @NotNull(message = "오더를 지정해주세요.")
    private Long orderId;

    // 컨테이너 번호를 눈으로 확인했는지 (true 만 허용)
    @NotNull(message = "컨테이너 번호 확인 여부를 입력해주세요.")
    @AssertTrue(message = "컨테이너 번호를 확인해야 인계할 수 있습니다.")
    private Boolean containerNoConfirmed;

    // 현장에서 읽은 씰 번호 (오더의 ACTIVE 씰과 같아야 함)
    @NotBlank(message = "씰 번호를 입력해주세요.")
    @Size(max = 30, message = "씰 번호는 30자 이하입니다.")
    private String sealNo;

    @NotNull(message = "손상 여부를 입력해주세요.")
    private Boolean damageFree;

    @NotBlank(message = "인수자 이름을 입력해주세요.")
    @Size(max = 50, message = "인수자 이름은 50자 이하입니다.")
    private String receiverName;

    private List<String> photoUrls;
}
