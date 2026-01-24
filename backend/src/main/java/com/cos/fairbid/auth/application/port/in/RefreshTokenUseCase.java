package com.cos.fairbid.auth.application.port.in;

/**
 * 토큰 갱신 유스케이스
 * Refresh Token으로 새로운 Access Token과 Refresh Token을 발급한다.
 * Token Rotation 정책이 적용되어 Refresh Token도 매번 갱신된다.
 */
public interface RefreshTokenUseCase {

    /**
     * 토큰을 갱신한다.
     * Refresh Token 검증 → 새 Access + Refresh Token 발급 → Redis 갱신
     *
     * @param refreshToken 기존 Refresh Token
     * @return 갱신된 토큰 결과
     */
    TokenResult refresh(String refreshToken);

    /**
     * 토큰 갱신 결과 record
     *
     * @param accessToken     새로운 Access Token
     * @param newRefreshToken 새로운 Refresh Token (Rotation 적용)
     * @param onboarded       온보딩 완료 여부
     */
    record TokenResult(
            String accessToken,
            String newRefreshToken,
            boolean onboarded
    ) {
    }
}
