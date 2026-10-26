package com.scargo.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

import java.util.List;

/**
 * fix_1330: prod 프로필인데 DB 환경변수(DB_URL·DB_USERNAME·DB_PASSWORD)가 없으면 기동 중단 (원인을 첫 줄에 분명히).
 * META-INF/spring.factories 로 등록. 설정 파일 로딩 뒤에 실행되도록 가장 낮은 우선순위.
 */
public class ProdEnvGuard implements EnvironmentPostProcessor, Ordered {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        if (!env.acceptsProfiles(Profiles.of("prod"))) return;
        List<String> missing = List.of("DB_URL", "DB_USERNAME", "DB_PASSWORD").stream()
                .filter(k -> { String v = env.getProperty(k); return v == null || v.isBlank(); }).toList();
        if (!missing.isEmpty())
            throw new IllegalStateException("[SCARGO] prod 프로필 기동 중단: 환경변수 " + String.join(", ", missing) + " 가 없습니다. DEPLOY_운영배포.md 참고");
    }

    @Override
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
}
