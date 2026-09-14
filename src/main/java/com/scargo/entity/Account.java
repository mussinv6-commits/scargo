package com.scargo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "user_name", length = 30)
    private String userName;

    @Column(name = "user_id", nullable = false, unique = true, length = 50)
    private String userId;

    @Column(name = "user_pw", nullable = false, length = 255)
    private String userPw;

    @Builder.Default
    @Column(name = "user_type", nullable = false, length = 30)
    private String userType = "GENERAL";

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "phone_num", length = 20)
    private String phoneNum;

    @Column(name = "business_no", length = 20)
    private String businessNo;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt;
}