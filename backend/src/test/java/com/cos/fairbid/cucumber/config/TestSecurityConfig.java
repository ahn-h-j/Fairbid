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
 * 인증 전략:
 * 1. userId 또는 bidderId 쿼리 파라미터가 있으면 해당 값으로 인증
 * 2. 파라미터가 없으면 URL 패턴에 따라 자동 분기:
 *    - 입찰 엔드포인트(/api/v1/auctions/{id}/bids) → 구매자 (userId=2)
 *    - 그 외 → 판매자 (userId=1)
 */
@TestConfiguration
public class TestSecurityConfig {

    /** 테스트용 판매자 ID */
    private static final Long SELLER_USER_ID = 1L;

    /** 테스트용 구매자 ID */
    private static final Long BUYER_USER_ID = 2L;

    private static final String TEST_NICKNAME = "TestUser";

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                .addFilterBefore(testAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 테스트용 모의 인증 필터
     * 요청의 userId/bidderId 파라미터 또는 URL 패턴에 따라 SecurityContext에 인증 정보를 설정한다.
     */
    @Bean
    public OncePerRequestFilter testAuthenticationFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                Long userId = extractUserId(request);
                // 테스트에서는 기본적으로 USER 역할 부여
                CustomUserDetails userDetails = new CustomUserDetails(userId, TEST_NICKNAME + userId, true, "USER");
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                filterChain.doFilter(request, response);
            }

            /**
             * 요청에서 사용자 ID를 추출한다.
             * 우선순위: userId 파라미터 > bidderId 파라미터 > URL 패턴 기반 자동 분기
             */
            private Long extractUserId(HttpServletRequest request) {
                // 1. userId 파라미터 확인
                String userIdParam = request.getParameter("userId");
                if (userIdParam != null && !userIdParam.isEmpty()) {
                    try {
                        return Long.parseLong(userIdParam);
                    } catch (NumberFormatException ignored) {
                    }
                }

                // 2. bidderId 파라미터 확인
                String bidderIdParam = request.getParameter("bidderId");
                if (bidderIdParam != null && !bidderIdParam.isEmpty()) {
                    try {
                        return Long.parseLong(bidderIdParam);
                    } catch (NumberFormatException ignored) {
                    }
                }

                // 3. URL 패턴에 따라 자동 분기
                return isBuyerEndpoint(request) ? BUYER_USER_ID : SELLER_USER_ID;
            }

            /**
             * 구매자 엔드포인트인지 판별한다.
             * 입찰, 결제 등 구매자 행위 엔드포인트이면 true
             */
            private boolean isBuyerEndpoint(HttpServletRequest request) {
                String path = request.getRequestURI();
                // 입찰 엔드포인트
                if (path.matches(".*/api/v1/auctions/\\d+/bids.*")) {
                    return true;
                }
                // 결제 엔드포인트
                if (path.matches(".*/api/v1/transactions/\\d+/payment.*")) {
                    return true;
                }
                return false;
            }
        };
    }
}
