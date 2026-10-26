package com.scargo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync            // 비동기 기능 활성화 
@EnableJpaAuditing      // JPA Auditing 기능 활성화
@EnableScheduling       // 26.10.21 v10: 반납 기한 알림(ReturnDueScheduler)·오래된 알림 정리
@SpringBootApplication
public class ScargoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScargoApplication.class, args);
    }
}