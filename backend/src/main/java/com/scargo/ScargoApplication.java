package com.scargo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync; 

@EnableAsync            // 비동기 기능 활성화 
@EnableJpaAuditing      // JPA Auditing 기능 활성화
@SpringBootApplication
public class ScargoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScargoApplication.class, args);
    }
}