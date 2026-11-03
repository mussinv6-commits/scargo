package com.scargo.amr.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/** 26.11.01 계정별 AMR 알림 읽음 */
@Entity
@Table(name = "amr_safety_alert_reads")
@Getter
@Setter
@NoArgsConstructor
@IdClass(AmrSafetyAlertRead.Pk.class)
public class AmrSafetyAlertRead {
    @Id
    @Column(name = "alert_id")
    private Long alertId;

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "read_at", nullable = false)
    private OffsetDateTime readAt = OffsetDateTime.now();

    public static class Pk implements Serializable {
        private Long alertId;
        private Long accountId;
        public Pk() {}
        public Pk(Long alertId, Long accountId) { this.alertId = alertId; this.accountId = accountId; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(alertId, pk.alertId) && Objects.equals(accountId, pk.accountId);
        }
        @Override public int hashCode() { return Objects.hash(alertId, accountId); }
    }
}
