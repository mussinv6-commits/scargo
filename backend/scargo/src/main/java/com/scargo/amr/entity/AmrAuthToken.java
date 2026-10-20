package com.scargo.amr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

// 26.10.16 AMR 관리자 OTP: amr_auth_tokens (OTP 통과 후 24시간 쓰는 출입증 · 원문 대신 SHA-256 해시만 저장)
@Entity
@Table(name = "amr_auth_tokens")
@Getter
@Setter
@NoArgsConstructor
public class AmrAuthToken {

    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "client", length = 10, nullable = false)
    private String client; // WEB / APP

    @Column(name = "key_id", nullable = false)
    private Long keyId; // 어느 OTP 키로 통과했는지 (키를 다시 등록하면 전부 무효)

    @Column(name = "ip", length = 60)
    private String ip;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "last_used_at")
    private OffsetDateTime lastUsedAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;
}
