package com.scargo.amr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 26.10.21 보안: AMR 앱 연결 코드 실패 감사 로그 (입력한 코드 값은 저장하지 않음) */
@Entity
@Table(name = "amr_auth_failures")
@Getter
@Setter
@NoArgsConstructor
public class AmrAuthFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "failure_id")
    private Long failureId;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "ip", length = 60)
    private String ip;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "reason", length = 30, nullable = false)
    private String reason;   // BAD_CODE | REUSED_CODE | IP_LOCKED | GLOBAL_LOCKED | NO_KEY
}
