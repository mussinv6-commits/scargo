package com.scargo.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "yards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Yard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "yard_id")    
    private Long yardId;  // 야드 고유 ID(야드 식별용)

    @Column(name = "yard_name", length = 50, nullable = false) 
    private String yardName;  // 야드 이름(A야드/제1컨테이너/위험물 컨테이너 등등)

    @Column(name = "yard_type", length = 30, nullable = false)
    private String yardType = "GENERAL";  // 야드용도(일반/냉동/위험물 등등 디폴트값은 일반(GENERAL))

    @Column(name = "latitude")
    private Double latitude;  // 야드 위도 좌표

    @Column(name = "longitude")
    private Double longitude; // 야드 경도 좌표

    @Column(name = "status", length = 20)
    private String status = "AVAILABLE";  // 야드 전체 상태(이용가능/점검중/이용 불가 등등)  

    @Column(name = "is_available")
    private Boolean isAvailable = true;  // 단순 사용 가능여부(t/f 값)

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)   // 생성일
    private OffsetDateTime createdAt;      

    @Builder
    public Yard(String yardName, String yardType, Double latitude, Double longitude, String status) {
        this.yardName = yardName;
        if (yardType != null) {
            this.yardType = yardType;
        }
        if (latitude != null) {
            this.latitude = latitude;
        }
        if (longitude != null) {
            this.longitude = longitude;
        }
        
        // status가 들어오면 isAvailable을 자동으로 동기화합니다.
        if (status != null) {
            this.status = status;
            this.isAvailable = "AVAILABLE".equals(status);
        }
    }
    
    // 야드 정보 수정 (is_available을 파라미터로 받지 않고 status 기반으로 자동 계산)
    public void update(String yardName, String yardType, String status, Double latitude, Double longitude) {
        if (yardName != null) {
            this.yardName = yardName;
        }
        if (yardType != null) {
            this.yardType = yardType;
        }
        if (latitude != null) {
            this.latitude = latitude;
        }
        if (longitude != null) {
            this.longitude = longitude;
        }
        
        // status가 수정되면 is_available도 무조건 함께 세팅됩니다!
        if (status != null) {
            this.status = status;
            this.isAvailable = "AVAILABLE".equals(status);
        }
    }
}