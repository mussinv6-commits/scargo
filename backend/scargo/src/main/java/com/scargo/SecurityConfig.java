package com.scargo;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus; // 26.10.01 추가
import org.springframework.security.web.authentication.HttpStatusEntryPoint; // 26.10.01 추가
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter; // 26.09.21 추가

import com.scargo.security.SessionAuthenticationFilter; // 26.09.21 추가
import lombok.RequiredArgsConstructor; // 26.09.21 추가

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity // 컨트롤러의 @PreAuthorize 권한 검증 활성화
@RequiredArgsConstructor // 26.09.21 추가
public class SecurityConfig {

    // 26.09.21 추가: 세션의 accountId를 Spring Security 인증 정보로 변환
    private final SessionAuthenticationFilter sessionAuthenticationFilter;

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

            // 26.09.21 추가: 세션의 accountId를 SecurityContext에 반영
            .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            // URL 접근 권한 설정
            .authorizeHttpRequests(auth -> auth
                // 1. 공통 비인증 허용 경로 (로그인, ID 중복체크, 회원가입 등)
                .requestMatchers(HttpMethod.GET, "/api/accounts/check-id/**").permitAll()
                // 26.10.01 추가: 스프링 내부 에러 페이지(/error)는 공개 - 막혀 있으면 404/500 같은
                // 실제 오류가 전부 "권한 없음(403)"으로 바뀌어 보여서 원인을 알 수 없었음
                .requestMatchers("/error").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/accounts", "/api/accounts/login").permitAll()

                // 2. 외부 연동 / 공개 API (매핑, 업체 등 서비스 요구사항에 맞게 설정)
                .requestMatchers("/api/mappings/**", "/api/mappings").permitAll()
                .requestMatchers("/api/companies/**").permitAll()

                // 2-1. 26.09.21 추가: 게시판은 비로그인 사용자도 조회는 가능해야 하므로 GET만 공개
                //      (글쓰기/수정/삭제 등은 anyRequest().authenticated() 규칙에 걸려 로그인 필요)
                .requestMatchers(HttpMethod.GET, "/api/v1/posts/**", "/api/comments/**").permitAll()
                // 26.09.30 병합(백엔드.zip): 게시판 /bbs 경로, 공지사항 GET 비회원 허용
                .requestMatchers(HttpMethod.GET, "/bbs/bbslist/**", "/bbs/bbslist", "/bbs/*/attachments/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/posts", "/api/posts/**", "/api/posts").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/notices/**", "/api/notices").permitAll()

                // 3. OCR 인식 장비/게이트 자동 전송 API가 비인증 접근이어야 할 경우 (주석 해제)
                // .requestMatchers(HttpMethod.POST, "/api/v1/gate-logs").permitAll()

                // 3-1. 26.10.01 병합(길웅님): 게이트(번호판 인식) 통과 현황 조회는 비회원도 실시간으로 볼 수 있게 공개
                //      (생성/수정/삭제는 GateLogController의 @PreAuthorize로 계속 보호됨)
                .requestMatchers(HttpMethod.GET, "/api/v1/gate-logs/**", "/api/v1/gate-logs").permitAll()

                // 4. 나머지 모든 API 요청은 로그인(인증) 필요
                // (각 컨트롤러의 @PreAuthorize("hasRole('ADMIN')") 등이 차례로 검증됨)
                .anyRequest().authenticated()
            )

            // 26.10.01 추가: 로그인 안 됨(세션 만료 포함)은 401, 로그인은 됐지만 권한 부족은 403 으로 구분
            //  - 기존엔 둘 다 403 이라 "백엔드 재시작으로 세션이 끊긴 것"과 "권한이 없는 것"을 화면에서 구분할 수 없었음
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

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