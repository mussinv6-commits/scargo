package com.scargo.app.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 26.10.03 2차: 기사 앱(APK) 로그인 토큰. 웹(1차)은 세션 쿠키를 그대로 쓴다. */
@Entity
@Table(name = "app_tokens")
@Getter
@Setter
@NoArgsConstructor
public class AppToken {
    @Id
    @Column(name = "token", length = 64)
    private String token;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;
}
