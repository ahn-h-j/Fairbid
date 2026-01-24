package com.cos.fairbid.auth.domain.exception;

/**
 * OAuth 이메일 미제공 예외
 * OAuth Provider에서 이메일 동의를 거부하여 이메일을 받을 수 없을 때 발생한다.
 * 카카오 등 이메일 선택동의 Provider에서 발생 가능하다.
 */
public class OAuthEmailRequiredException extends RuntimeException {

    private OAuthEmailRequiredException(String message) {
        super(message);
    }

    /**
     * OAuth 인증 시 이메일이 제공되지 않았을 때 사용한다.
     *
     * @param provider OAuth Provider 이름
     */
    public static OAuthEmailRequiredException from(String provider) {
        return new OAuthEmailRequiredException(
                provider + " 로그인 시 이메일 동의가 필요합니다. 이메일 제공에 동의해주세요.");
    }
}
