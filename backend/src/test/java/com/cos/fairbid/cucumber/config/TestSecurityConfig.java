package com.cos.fairbid.cucumber.config;

import com.cos.fairbid.auth.infrastructure.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 테스트 환경용 Security 설정
 * 기존 통합 테스트(Cucumber)가 인증 없이 동작하도록 모든 요청을 허용한다.
 * 프로덕션 SecurityConfig는 @Profile("!test")로 비활성화된다.
 *
 * 추가 기능:
 * - userId 쿼리 파라미터가 있으면 해당 사용자로 인증 컨텍스트 설정
 * - bidderId 쿼리 파라미터가 있으면 해당 사용자로 인증 컨텍스트 설정 (기본값: 2)
 */
@TestConfiguration
public class TestSecurityConfig {

    /** 테스트용 기본 사용자 ID (입찰자/구매자) */
    private static final Long DEFAULT_USER_ID = 2L;

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(new MockAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                );
        return http.build();
    }

    /**
     * 테스트용 모의 인증 필터
     * 요청의 userId 또는 bidderId 파라미터를 읽어 SecurityContext에 인증 정보를 설정한다.
     * 파라미터가 없으면 기본 사용자 ID(2)로 설정한다.
     */
    private static class MockAuthenticationFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            // userId 파라미터 우선, 없으면 bidderId, 둘 다 없으면 기본값
            Long userId = extractUserId(request);

            // CustomUserDetails 생성 (테스트용: 온보딩 완료 상태로 설정)
            CustomUserDetails userDetails = new CustomUserDetails(userId, "TestUser" + userId, true);

            // Authentication 객체 생성 및 SecurityContext에 설정
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        }

        /**
         * 요청에서 사용자 ID를 추출한다.
         * userId > bidderId > 기본값 순으로 우선순위를 적용한다.
         */
        private Long extractUserId(HttpServletRequest request) {
            String userIdParam = request.getParameter("userId");
            if (userIdParam != null && !userIdParam.isEmpty()) {
                try {
                    return Long.parseLong(userIdParam);
                } catch (NumberFormatException ignored) {
                    // 파싱 실패 시 다음 옵션으로
                }
            }

            String bidderIdParam = request.getParameter("bidderId");
            if (bidderIdParam != null && !bidderIdParam.isEmpty()) {
                try {
                    return Long.parseLong(bidderIdParam);
                } catch (NumberFormatException ignored) {
                    // 파싱 실패 시 기본값
                }
            }

            return DEFAULT_USER_ID;
        }
    }
}
