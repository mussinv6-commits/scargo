package com.scargo;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy; // 추가
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity // 권한 부여 기능 이용시 필요
public class SecurityConfig {

    // 회원가입시 비밀번호 암호화
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // CORS 설정
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        // Vue 개발 서버 주소 (localhost 및 127.0.0.1 모두 허용)
        configuration.setAllowedOrigins(
            List.of("http://localhost:5173", "http://127.0.0.1:5173")
        );

        // 허용할 HTTP Method
        configuration.setAllowedMethods(
            List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        );

        // 요청 헤더 허용
        configuration.setAllowedHeaders(
            List.of("*")
        );

        // 쿠키/세션 사용 허용
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    // 시큐리티 필터 체인 설정
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            // CORS 설정 명시
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // REST API 환경에서 CSRF 비활성화
            .csrf(csrf -> csrf.disable())

            // ★ 세션 관리 정책 추가 (필요 시 세션 생성 및 기존 세션 재사용 설정)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            // URL 접근 권한 설정
            .authorizeHttpRequests(auth -> auth
                // 1. 매핑 관련 API 전체 허용
                .requestMatchers("/api/mappings/**", "/api/mappings").permitAll()
                
                // 2. 기타 인증 제외 경로들
                .requestMatchers("/api/companies/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/accounts/check-id/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/accounts", "/api/accounts/login").permitAll()

                // 3. 나머지는 인증 요구
                .anyRequest().authenticated()
            )

            .logout(logout -> logout
                .logoutUrl("/api/accounts/logout")
                .logoutSuccessHandler((request, response, authentication) -> {
                    response.setStatus(200);
                    response.setCharacterEncoding("UTF-8");
                    response.setContentType("text/plain;charset=UTF-8");
                    response.getWriter().write("로그아웃 성공");
                })
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            );

        return http.build();
    }
}