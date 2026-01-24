package com.cos.fairbid.auth.application.service;

import com.cos.fairbid.auth.adapter.out.oauth.OAuthUserInfo;
import com.cos.fairbid.auth.application.port.in.LogoutUseCase;
import com.cos.fairbid.auth.application.port.in.OAuthLoginUseCase;
import com.cos.fairbid.auth.application.port.in.RefreshTokenUseCase;
import com.cos.fairbid.auth.application.port.out.OAuthClientPort;
import com.cos.fairbid.auth.application.port.out.RefreshTokenPort;
import com.cos.fairbid.auth.domain.exception.RefreshTokenReusedException;
import com.cos.fairbid.auth.infrastructure.jwt.JwtTokenProvider;
import com.cos.fairbid.user.application.port.out.LoadUserPort;
import com.cos.fairbid.user.application.port.out.SaveUserPort;
import com.cos.fairbid.user.domain.OAuthProvider;
import com.cos.fairbid.user.domain.User;
import com.cos.fairbid.user.domain.exception.UserBlockedException;
import com.cos.fairbid.user.domain.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 서비스
 * OAuth 로그인, 토큰 갱신, 로그아웃을 처리하는 핵심 서비스이다.
 *
 * - login: OAuth Code → 사용자 조회/생성 → 차단 체크 → JWT 발급 → Redis 저장
 * - refresh: Redis 검증 → Token Rotation → 새 토큰 발급
 * - logout: Redis 삭제
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService implements OAuthLoginUseCase, RefreshTokenUseCase, LogoutUseCase {

    private final OAuthClientPort oAuthClientPort;
    private final LoadUserPort loadUserPort;
    private final SaveUserPort saveUserPort;
    private final RefreshTokenPort refreshTokenPort;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * OAuth 로그인을 수행한다.
     *
     * 흐름:
     * 1. OAuth Provider에서 사용자 정보 조회
     * 2. DB에서 기존 사용자 조회 (provider + providerId)
     * 3. 없으면 신규 생성
     * 4. 차단 상태 체크
     * 5. JWT 토큰 발급 (Access + Refresh)
     * 6. Refresh Token을 Redis에 저장 (단일 세션: 기존 토큰 덮어씀)
     */
    @Override
    @Transactional
    public LoginResult login(OAuthProvider provider, String code) {
        // 1. OAuth Provider에서 사용자 정보 조회
        OAuthUserInfo userInfo = oAuthClientPort.getUserInfo(provider, code);
        log.debug("OAuth 로그인 시도: provider={}, email={}", provider, maskEmail(userInfo.email()));

        // 2. 기존 사용자 조회
        boolean isNewUser = false;
        User user = loadUserPort.findByProviderAndProviderId(provider, userInfo.providerId())
                .orElse(null);

        // 3. 신규 사용자 생성
        if (user == null) {
            user = User.create(userInfo.email(), provider, userInfo.providerId());
            user = saveUserPort.save(user);
            isNewUser = true;
            log.info("신규 사용자 가입: userId={}, provider={}", user.getId(), provider);
        }

        // 4. 차단 상태 체크
        if (user.isBlocked()) {
            if (!user.isActive()) {
                throw UserBlockedException.byDeactivation();
            }
            throw UserBlockedException.byWarningCount();
        }

        // 5. JWT 토큰 발급
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        // 6. Refresh Token Redis 저장 (단일 세션 정책: 기존 세션 무효화)
        refreshTokenPort.save(user.getId(), refreshToken, jwtTokenProvider.getRefreshExpirationSeconds());

        return new LoginResult(user, accessToken, refreshToken, isNewUser);
    }

    /**
     * 토큰을 갱신한다. (Token Rotation 적용)
     *
     * 흐름:
     * 1. Refresh Token에서 userId 추출 (유효성 검증 포함)
     * 2. Redis에 저장된 토큰과 일치하는지 확인 (재사용 감지)
     * 3. 사용자 조회
     * 4. 새 토큰 발급 (Access + Refresh)
     * 5. Redis에 새 Refresh Token 저장 (기존 토큰 무효화)
     */
    @Override
    public TokenResult refresh(String refreshToken) {
        // 1. Refresh Token 유효성 검증 + userId 추출
        Long userId = jwtTokenProvider.getUserIdFromRefreshToken(refreshToken);

        // 2. Redis에 저장된 토큰과 일치하는지 확인 (Token Rotation 재사용 감지)
        if (!refreshTokenPort.matches(userId, refreshToken)) {
            // 재사용 감지: 탈취 가능성 → 해당 사용자의 모든 세션 무효화
            refreshTokenPort.delete(userId);
            log.warn("Refresh Token 재사용 감지! userId={}", userId);
            throw RefreshTokenReusedException.detected(userId);
        }

        // 3. 사용자 조회
        User user = loadUserPort.findById(userId)
                .orElseThrow(() -> UserNotFoundException.withId(userId));

        // 4. 새 토큰 발급
        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user);

        // 5. Redis 갱신 (Token Rotation: 이전 토큰 무효화)
        refreshTokenPort.save(userId, newRefreshToken, jwtTokenProvider.getRefreshExpirationSeconds());

        return new TokenResult(newAccessToken, newRefreshToken, user.isOnboarded());
    }

    /**
     * 로그아웃을 수행한다.
     * Redis에서 Refresh Token을 삭제하여 해당 세션을 무효화한다.
     */
    @Override
    public void logout(Long userId) {
        refreshTokenPort.delete(userId);
        log.info("로그아웃 완료: userId={}", userId);
    }

    /**
     * 이메일을 마스킹한다. (PII 보호)
     * 예: "user@example.com" → "u***@example.com"
     *
     * @param email 원본 이메일
     * @return 마스킹된 이메일
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf("@");
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (local.length() <= 1) {
            return "*" + domain;
        }
        return local.charAt(0) + "***" + domain;
    }
}
