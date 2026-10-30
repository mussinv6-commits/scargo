package com.scargo;

import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
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
import com.scargo.amr.security.AmrTokenAuthenticationFilter; // 26.10.22 추가 (share)
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

    // 26.10.03 2차 추가: 기사 앱(APK)은 세션 쿠키 대신 Authorization: Bearer 토큰 사용
    private final com.scargo.app.security.AppTokenAuthenticationFilter appTokenAuthenticationFilter;
    // 26.10.21 역할 분리: S-Bot 관리자는 AMR 외 /api/** 403
    private final com.scargo.security.SbotScopeFilter sbotScopeFilter;
    // 26.10.22 (share): 허브 앱 출입증(X-AMR-Token)을 허브 범위가 있는 인증으로 변환 (/api/amr/** 전용)
    private final AmrTokenAuthenticationFilter amrTokenAuthenticationFilter;

    @Bean
    public FilterRegistrationBean<AmrTokenAuthenticationFilter> amrTokenAuthenticationFilterRegistration(
            AmrTokenAuthenticationFilter filter) {
        FilterRegistrationBean<AmrTokenAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<com.scargo.security.SbotScopeFilter> sbotScopeFilterRegistration(com.scargo.security.SbotScopeFilter f) {
        FilterRegistrationBean<com.scargo.security.SbotScopeFilter> r = new FilterRegistrationBean<>(f);
        r.setEnabled(false);
        return r;
    }

    // @Component 필터가 서블릿 필터로 한 번 더 등록되면 시큐리티 체인 밖에서 돌고,
    // 체인 시작 시 SecurityContext가 비워져 로그인 사용자가 익명으로 남는다.
    @Bean
    public FilterRegistrationBean<SessionAuthenticationFilter> sessionAuthenticationFilterRegistration(
            SessionAuthenticationFilter filter) {
        FilterRegistrationBean<SessionAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    // 26.10.03 2차 추가: 토큰 필터도 시큐리티 체인 안에서만 돌도록 서블릿 자동 등록을 끈다 (위와 같은 이유)
    @Bean
    public FilterRegistrationBean<com.scargo.app.security.AppTokenAuthenticationFilter> appTokenAuthenticationFilterRegistration(
            com.scargo.app.security.AppTokenAuthenticationFilter filter) {
        FilterRegistrationBean<com.scargo.app.security.AppTokenAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

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
        // 26.10.03 2차 변경: 기사 앱 개발 서버(5174), APK(http://localhost), 같은 와이파이의 휴대폰 브라우저도 허용
        //   allowCredentials(true) 와 함께 쓰려면 setAllowedOrigins 대신 패턴 방식이어야 함
        configuration.setAllowedOriginPatterns(
                List.of("http://localhost:5173", "http://127.0.0.1:5173",
                        "http://localhost:*", "http://127.0.0.1:*", "http://localhost", "https://localhost", "capacitor://localhost",
                        "http://192.168.*.*:*", "http://10.*.*.*:*", "http://172.*.*.*:*")
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
                // v13: 로그인 뒤 첫 인증 요청에서 세션 ID 를 바꾸지 않음 (ID 교체는 로그인 API 에서 1번).
                //      동시에 나간 요청이 옛 ID 로 401 → 화면이 로그아웃되던 문제(간헐적, 역할 무관) 수정
                .sessionFixation(f -> f.none())
            )

            // v13: 401 응답 때 요청 저장(RequestCache)용 익명 세션을 만들지 않음.
            //      로그인 직후 겹친 익명 요청이 새 JSESSIONID 를 내려 로그인 쿠키를 덮어쓰던 문제(간헐적 로그아웃) 수정
            .requestCache(rc -> rc.disable())

            // 26.09.21 추가: 세션의 accountId를 SecurityContext에 반영
            .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // 26.10.03 2차 추가: Bearer 토큰 → SecurityContext (세션이 없는 APK 요청용, 세션 필터와 독립적으로 동작)
            .addFilterBefore(appTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // 26.10.22 (share): 웹 로그인 세션이 없을 때만 허브 앱 출입증으로 인증
            .addFilterAfter(amrTokenAuthenticationFilter, SessionAuthenticationFilter.class)
            .addFilterAfter(sbotScopeFilter, UsernamePasswordAuthenticationFilter.class)

            // URL 접근 권한 설정
            .authorizeHttpRequests(auth -> auth
                // 1. 공통 비인증 허용 경로 (로그인, ID 중복체크, 회원가입 등)
                .requestMatchers(HttpMethod.GET, "/api/accounts/check-id/**").permitAll()
                // 26.10.01 추가: 스프링 내부 에러 페이지(/error)는 공개 - 막혀 있으면 404/500 같은
                // 실제 오류가 전부 "권한 없음(403)"으로 바뀌어 보여서 원인을 알 수 없었음
                .requestMatchers("/error").permitAll()
                // 26.10.21 v4 병합(팀장 v3): Swagger UI·API 명세, AMR 관제 API
                //   /api/amr/** 는 Spring Security 대신 AmrAuthConfig 인터셉터가 검사 (ADMIN·AMR 관리자 세션 또는 사람에 묶인 X-AMR-Token)
                // fix_1300: 운영(prod 프로필)에서는 springdoc.api-docs/swagger-ui 자체를 끔 (application-prod.yml) → 이 경로는 404
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                // 통합(share 26.10.22): 전체 공개 → 연결 확인 · 앱 인증(코드 입력)만 공개.
                //  나머지 /api/amr/** 는 웹 로그인 또는 허브 앱 출입증이 필요하고, 역할 · 허브 범위는 AmrController · AmrAccessPolicy 가 확인
                .requestMatchers("/api/amr/health", "/api/amr/auth/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/accounts", "/api/accounts/login").permitAll()
                // 26.10.03 2차 추가: 기사 앱 로그인 / 서버 연결 확인
                .requestMatchers(HttpMethod.POST, "/api/app/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/app/health").permitAll()
                // 26.10.21 v9: 기사 앱 회원가입 (A-02)
                .requestMatchers(HttpMethod.GET, "/api/app/signup/carriers").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/app/signup").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // 2. 외부 연동 / 공개 API (매핑, 업체 등 서비스 요구사항에 맞게 설정)
                // fix_1300: /api/mappings/** 공개 삭제 (로그인 필요, 컨트롤러가 세션 계정 검사)
                // fix_1300: 회사 API 는 가입 화면용 GET 목록과 가입 중 새 업체 등록만 공개. 수정·삭제·관리자 등록은 ADMIN (@PreAuthorize)
                .requestMatchers(HttpMethod.GET, "/api/companies/options").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/companies/signup").permitAll()

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
                // fix_1300: 비회원 공개 해제 (차량번호·통과 시각이 노출되던 문제). 관리자 화면은 세션으로 조회
                .requestMatchers(HttpMethod.GET, "/api/v1/gate-logs/**", "/api/v1/gate-logs").authenticated()
                // fix_1300: 업로드 파일(인계 사진·반납 증빙·검사 사진·첨부)은 로그인 사용자만
                .requestMatchers(HttpMethod.GET, "/uploads/**").authenticated()

                // 26.10.21 v9: 승인 전(PENDING/REJECTED) 기사는 승인 상태 조회만 가능
                .requestMatchers("/api/app/approval", "/api/app/auth/**").authenticated()
                // fix_1027: 승인 대기·반려 기사도 본인 알림(가입 승인/반려 결과)은 볼 수 있게 (본인 account_id 알림만 반환)
                .requestMatchers(HttpMethod.GET, "/api/app/notifications").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/app/notifications/read-all").authenticated()
                .requestMatchers("/api/app/**", "/api/web/**").access((au, ctx) -> new org.springframework.security.authorization.AuthorizationDecision(
                        au.get().isAuthenticated() && au.get().getAuthorities().stream().noneMatch(g -> "ROLE_PENDING_DRIVER".equals(g.getAuthority()) || "ROLE_ANONYMOUS".equals(g.getAuthority()))
                        && au.get().getAuthorities().stream().anyMatch(g -> g.getAuthority().startsWith("ROLE_"))))

                // 4. 나머지 모든 API 요청은 로그인(인증) 필요
                // (각 컨트롤러의 @PreAuthorize("hasRole('ADMIN')") 등이 차례로 검증됨)
                .anyRequest().authenticated()
            )

            // 로그아웃 설정
            // 26.10.01 추가: 로그인 안 됨(세션 만료 포함)은 401, 로그인은 됐지만 권한 부족은 403 으로 구분
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

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