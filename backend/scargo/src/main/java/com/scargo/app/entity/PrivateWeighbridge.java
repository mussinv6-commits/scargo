package com.scargo.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** 26.10.03 2차: 민간계량소 - 출처: 전국 민간계량소.json (1차의 검사소 계량과는 별개) */
// 26.10.20: 통합 DB(11차)에 private_weighbridges 테이블이 없음(삭제 목록) → JPA 매핑 해제. TODO(DB 설계): 민간계량소 데이터를 둘 테이블 결정
@Getter
@Setter
@NoArgsConstructor
public class PrivateWeighbridge {
    private Long weighbridgeId;
    private String sourceId;
    private String businessName;
    private String status;
    private String phone;
    private String address;
    private String roadAddress;
    private String sido;
    private String sigungu;
    private BigDecimal latitude;
    private BigDecimal longitude;
}
