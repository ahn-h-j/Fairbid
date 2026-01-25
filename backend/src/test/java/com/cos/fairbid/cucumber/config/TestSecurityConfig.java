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
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 테스트 환경용 Security 설정
 * 기존 통합 테스트(Cucumber)가 인증 없이 동작하도록 모든 요청을 허용한다.
 * 프로덕션 SecurityConfig는 @Profile("!test")로 비활성화된다.
 *
 * 기본 테스트 사용자 (userId=1, onboarded=true)를 SecurityContext에 설정하여
 * SecurityUtils.getCurrentUserId() 호출이 정상 동작하도록 한다.
 */
@TestConfiguration
public class TestSecurityConfig {

    /**
     * 테스트용 판매자 ID (기존 AuctionController에서 하드코딩 sellerId=1L 유지)
     */
    private static final Long SELLER_USER_ID = 1L;

    /**
     * 테스트용 구매자 ID (기존 BidController에서 X-User-Id 기본값=2 유지)
     */
    private static final Long BUYER_USER_ID = 2L;
    private static final String TEST_NICKNAME = "TestUser";

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                .addFilterBefore(testAuthenticationFilter(), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 모든 요청에 테스트 사용자 인증 정보를 설정하는 필터
     *
     * URL 패턴에 따라 userId를 분리하여 self-bid 검증을 통과한다:
     * - /api/v1/auctions/{id}/bids 엔드포인트 → 구매자 (userId=2)
     * - 그 외 → 판매자 (userId=1)
     */
    @Bean
    public OncePerRequestFilter testAuthenticationFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                Long userId = isBidEndpoint(request) ? BUYER_USER_ID : SELLER_USER_ID;
                CustomUserDetails userDetails = new CustomUserDetails(userId, TEST_NICKNAME, true);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                filterChain.doFilter(request, response);
            }

            /**
             * 입찰 엔드포인트인지 판별한다.
             * /api/v1/auctions/{id}/bids 형태의 POST 요청이면 구매자로 인증한다.
             */
            private boolean isBidEndpoint(HttpServletRequest request) {
                String path = request.getRequestURI();
                return path.matches(".*/api/v1/auctions/\\d+/bids.*");
            }
        };
    }
}
