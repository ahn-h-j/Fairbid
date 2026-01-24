package com.cos.fairbid.auth.domain.exception;

/**
 * JWT 토큰 유효하지 않음 예외
 * 서명 불일치, 형식 오류 등 토큰 자체가 유효하지 않을 때 발생한다.
 */
public class TokenInvalidException extends RuntimeException {

    private TokenInvalidException(String message) {
        super(message);
    }

    /**
     * 토큰 형식이 잘못되었거나 서명이 유효하지 않을 때 사용한다.
     */
    public static TokenInvalidException malformed() {
        return new TokenInvalidException("유효하지 않은 토큰입니다.");
    }
}
