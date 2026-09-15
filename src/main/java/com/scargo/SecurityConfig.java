package com.scargo;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity  //권한 부여기능 이용시 필요 (@PreAuthorize 이용시) 
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

        // Vue 개발 서버 주소
        configuration.setAllowedOrigins(
            List.of("http://localhost:5173")
        );

        // 허용할 HTTP Method
        configuration.setAllowedMethods(
            List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
        );

        // 요청 헤더 허용
        configuration.setAllowedHeaders(
            List.of("*")
        );

        // 쿠키/세션 사용 허용
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    // 시큐리티 필터 체인 설정
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            // CORS 활성화
            .cors(cors -> {})

            // REST API 환경에서 CSRF 비활성화
            .csrf(csrf -> csrf.disable())

            .authorizeHttpRequests(auth -> auth

            	    // 회사 관련 API 인증 없이 허용
            	    .requestMatchers("/api/companies/**").permitAll()

            	    // 아이디 중복확인 API 인증 없이 허용 (추가)
            	    .requestMatchers(HttpMethod.GET, "/api/accounts/check-id/**").permitAll()

            	    // 회원가입 및 로그인 API 인증 없이 허용
            	    .requestMatchers(
            	        HttpMethod.POST,
            	        "/api/accounts",
            	        "/api/accounts/login"
            	    ).permitAll()

            	    // 나머지는 인증 필요
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