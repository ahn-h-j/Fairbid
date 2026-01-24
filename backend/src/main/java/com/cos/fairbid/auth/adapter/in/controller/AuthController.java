package com.cos.fairbid.auth.adapter.in.controller;

import com.cos.fairbid.auth.adapter.in.dto.TokenResponse;
import com.cos.fairbid.auth.application.port.in.LogoutUseCase;
import com.cos.fairbid.auth.application.port.in.OAuthLoginUseCase;
import com.cos.fairbid.auth.application.port.in.RefreshTokenUseCase;
import com.cos.fairbid.auth.infrastructure.security.SecurityUtils;
import com.cos.fairbid.user.domain.OAuthProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 인증 컨트롤러
 *
 * OAuth2 로그인 흐름:
 * 1. GET /api/v1/auth/oauth2/{provider} → Provider 인증 페이지로 리다이렉트
 * 2. GET /api/v1/auth/oauth2/callback/{provider} → 콜백 처리 → Refresh 쿠키 설정 → 프론트로 리다이렉트
 * 3. POST /api/v1/auth/refresh → Access Token 재발급 (프론트가 콜백 후 호출)
 * 4. POST /api/v1/auth/logout → Refresh Token 삭제 + 쿠키 제거
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final int REFRESH_TOKEN_MAX_AGE = 14 * 24 * 60 * 60; // 2주 (초)

    private final OAuthLoginUseCase oAuthLoginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    @Value("${oauth2.kakao.client-id:}")
    private String kakaoClientId;

    @Value("${oauth2.kakao.redirect-uri:}")
    private String kakaoRedirectUri;

    @Value("${oauth2.naver.client-id:}")
    private String naverClientId;

    @Value("${oauth2.naver.redirect-uri:}")
    private String naverRedirectUri;

    @Value("${oauth2.google.client-id:}")
    private String googleClientId;

    @Value("${oauth2.google.redirect-uri:}")
    private String googleRedirectUri;

    /**
     * OAuth Provider 인증 페이지로 리다이렉트한다.
     * 프론트엔드에서 로그인 버튼 클릭 시 호출된다.
     *
     * @param provider OAuth Provider (kakao, naver, google)
     */
    @GetMapping("/oauth2/{provider}")
    public void redirectToProvider(@PathVariable String provider, HttpServletResponse response) throws Exception {
        OAuthProvider oAuthProvider = OAuthProvider.valueOf(provider.toUpperCase());
        String authorizationUrl = buildAuthorizationUrl(oAuthProvider);
        response.sendRedirect(authorizationUrl);
    }

    /**
     * OAuth 콜백을 처리한다.
     * Provider에서 Authorization Code를 받아 로그인/가입 처리 후
     * Refresh Token을 HttpOnly 쿠키에 설정하고 프론트엔드로 리다이렉트한다.
     *
     * @param provider OAuth Provider (kakao, naver, google)
     * @param code     Authorization Code
     */
    @GetMapping("/oauth2/callback/{provider}")
    public void handleCallback(@PathVariable String provider,
                               @RequestParam String code,
                               HttpServletResponse response) throws Exception {
        OAuthProvider oAuthProvider = OAuthProvider.valueOf(provider.toUpperCase());

        // OAuth 로그인 처리
        OAuthLoginUseCase.LoginResult result = oAuthLoginUseCase.login(oAuthProvider, code);

        // Refresh Token을 HttpOnly 쿠키에 설정
        setRefreshTokenCookie(response, result.refreshToken());

        // 프론트엔드 콜백 페이지로 리다이렉트
        // 프론트에서 /auth/callback 로드 → POST /api/v1/auth/refresh 호출 → Access Token 수신
        String redirectUrl = frontendUrl + "/auth/callback";
        response.sendRedirect(redirectUrl);
    }

    /**
     * Access Token을 갱신한다.
     * Refresh Token 쿠키를 사용하여 새로운 Access Token을 발급한다.
     * Token Rotation: Refresh Token도 새로 발급되어 쿠키에 재설정된다.
     *
     * @param refreshToken 쿠키에서 전달된 Refresh Token
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401).build();
        }

        // 토큰 갱신 (Token Rotation 적용)
        RefreshTokenUseCase.TokenResult result = refreshTokenUseCase.refresh(refreshToken);

        // 새 Refresh Token 쿠키 설정
        setRefreshTokenCookie(response, result.newRefreshToken());

        // Access Token + onboarded 정보 응답
        // onboarded 판단은 Access Token 내 클레임에서 추출 가능하지만,
        // 최초 콜백 시에는 SecurityContext가 없으므로 별도로 제공하지 않음
        // → 프론트는 Access Token 디코딩으로 판단
        return ResponseEntity.ok(new TokenResponse(result.accessToken(), true));
    }

    /**
     * 로그아웃을 수행한다.
     * Redis에서 Refresh Token을 삭제하고 쿠키를 제거한다.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        Long userId = SecurityUtils.getCurrentUserId();
        logoutUseCase.logout(userId);

        // Refresh Token 쿠키 제거
        clearRefreshTokenCookie(response);

        return ResponseEntity.ok().build();
    }

    /**
     * Refresh Token을 HttpOnly, Secure, SameSite=Strict 쿠키에 설정한다.
     */
    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(REFRESH_TOKEN_MAX_AGE);
        // SameSite=Strict는 Cookie API로 직접 설정 불가, 헤더로 추가
        response.addHeader("Set-Cookie",
                REFRESH_TOKEN_COOKIE + "=" + refreshToken
                        + "; Path=/api/v1/auth"
                        + "; Max-Age=" + REFRESH_TOKEN_MAX_AGE
                        + "; HttpOnly"
                        + "; Secure"
                        + "; SameSite=Strict");
    }

    /**
     * Refresh Token 쿠키를 제거한다.
     */
    private void clearRefreshTokenCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie",
                REFRESH_TOKEN_COOKIE + "="
                        + "; Path=/api/v1/auth"
                        + "; Max-Age=0"
                        + "; HttpOnly"
                        + "; Secure"
                        + "; SameSite=Strict");
    }

    /**
     * OAuth Provider별 인증 URL을 생성한다.
     */
    private String buildAuthorizationUrl(OAuthProvider provider) {
        return switch (provider) {
            case KAKAO -> "https://kauth.kakao.com/oauth/authorize"
                    + "?client_id=" + kakaoClientId
                    + "&redirect_uri=" + kakaoRedirectUri
                    + "&response_type=code"
                    + "&scope=account_email";
            case NAVER -> "https://nid.naver.com/oauth2.0/authorize"
                    + "?client_id=" + naverClientId
                    + "&redirect_uri=" + naverRedirectUri
                    + "&response_type=code";
            case GOOGLE -> "https://accounts.google.com/o/oauth2/v2/auth"
                    + "?client_id=" + googleClientId
                    + "&redirect_uri=" + googleRedirectUri
                    + "&response_type=code"
                    + "&scope=email profile";
        };
    }
}
