package com.scargo.amr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

// 26.10.16 AMR 관리자 OTP: amr_otp_keys (구글 OTP 앱에 등록한 비밀키 · 활성 1개만 사용)
@Entity
@Table(name = "amr_otp_keys")
@Getter
@Setter
@NoArgsConstructor
public class AmrOtpKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "key_id")
    private Long keyId;

    @Column(name = "label", length = 40, nullable = false)
    private String label; // OTP 앱에 보이는 이름 (예: admin)

    @Column(name = "secret", length = 64, nullable = false)
    private String secret; // Base32 비밀키 (서버와 휴대폰만 앎)

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_by", length = 50)
    private String createdBy; // 등록한 관리자 아이디

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;
}
