package com.cos.fairbid.auth.domain.exception;

/**
 * JWT 토큰 만료 예외
 * Access Token 또는 Refresh Token이 만료되었을 때 발생한다.
 */
public class TokenExpiredException extends RuntimeException {

    private TokenExpiredException(String message) {
        super(message);
    }

    /**
     * Access Token 만료 시 사용한다.
     */
    public static TokenExpiredException accessToken() {
        return new TokenExpiredException("Access Token이 만료되었습니다.");
    }

    /**
     * Refresh Token 만료 시 사용한다.
     */
    public static TokenExpiredException refreshToken() {
        return new TokenExpiredException("Refresh Token이 만료되었습니다. 다시 로그인해주세요.");
    }
}
