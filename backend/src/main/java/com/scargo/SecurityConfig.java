package com.scargo;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity // 컨트롤러의 @PreAuthorize 권한 검증 활성화
public class SecurityConfig {

    // 회원가입 및 비밀번호 암호화
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

            // 세션 관리 정책 (인증 시 세션 생성)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            // URL 접근 권한 설정
            .authorizeHttpRequests(auth -> auth
                // 1. 공통 비인증 허용 경로 (로그인, ID 중복체크, 회원가입 등)
                .requestMatchers(HttpMethod.GET, "/api/accounts/check-id/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/accounts", "/api/accounts/login").permitAll()

                // 2. 외부 연동 / 공개 API
                .requestMatchers("/api/mappings/**", "/api/mappings").permitAll()
                .requestMatchers("/api/companies/**").permitAll()

                // 3. 게시판 및 공지사항 조회(GET) 비회원 접근 허용
                // 프론트엔드에서 요청하는 /bbs/bbslist 경로 추가
                .requestMatchers(HttpMethod.GET, "/bbs/bbslist/**", "/bbs/bbslist").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/posts/**", "/api/v1/posts").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/posts/**", "/api/posts").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/notices/**", "/api/notices").permitAll()

                // 4. 게이트(번호판 인식) 통과 현황 조회는 비회원도 실시간으로 볼 수 있게 공개
                //    (생성/수정/삭제는 GateLogController의 @PreAuthorize로 계속 보호됨)
                .requestMatchers(HttpMethod.GET, "/api/v1/gate-logs/**", "/api/v1/gate-logs").permitAll()

                // 5. 나머지 모든 API 요청은 로그인(인증) 필요
                .anyRequest().authenticated()
            )

            // 로그아웃 설정
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