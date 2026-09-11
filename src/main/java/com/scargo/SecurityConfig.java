package com.scargo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // 회원가입시 비밀번호 암호화(BcryptPasswordEncoder) 
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 시큐리티 필터 체인 설정 (API 접근 허용 및 CSRF 비활성화)
    // 로그인 필요없는 기능 이용시 추가해야함 -> 추가 안할시 보안에러(401) 발생
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // REST API 환경에서 POST 등 요청을 위해 비활성화 
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/companies/**").permitAll() // 회사 관련 API는 인증 없이 허용
                .requestMatchers(HttpMethod.POST, "/api/accounts", "/api/accounts/login").permitAll() // 회원가입 및 로그인 API 허용
                .anyRequest().authenticated() // 그 외 요청은 인증 필요
            )
            .logout(logout -> logout
                .logoutUrl("/api/accounts/logout") // 로그아웃 요청 경로
                .logoutSuccessHandler((request, response, authentication) -> {
                    response.setStatus(200);
                    response.setCharacterEncoding("UTF-8"); // 한글 인코딩 설정 추가
                    response.setContentType("text/plain;charset=UTF-8"); // 응답 Content-Type 설정 추가
                    response.getWriter().write("로그아웃 성공");
                })
                .invalidateHttpSession(true) // 세션 무효화
                .deleteCookies("JSESSIONID") // 쿠키 삭제
            );

        return http.build();
    }
}