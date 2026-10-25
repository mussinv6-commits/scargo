package com.scargo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

// 26.10.23 컨테이너 점검표 작성. 업체 · 허브는 서버가 로그인 계정과 검증된 오더 연결로 정하므로 받지 않는다
//  판정(overallStatus)도 받지 않고 서버가 items 로 계산한다
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ContainerInspectionCreateRequest {

    @NotBlank(message = "컨테이너 번호는 필수 입력 항목입니다.")
    @Size(max = 11, message = "컨테이너 번호는 11자입니다.")
    private String containerNo;

    // 선택: 연결할 오더. 비우면 진행 중 오더가 정확히 1건일 때만 자동 연결
    private Long orderId;

    // {"front_wall": true, ...} true = 양호. 일부만 보내도 됨 (7개가 다 들어오기 전에는 판정 PENDING)
    private Map<String, Boolean> items;

    @Size(max = 1000, message = "메모는 1000자 이하입니다.")
    private String memo;
}
