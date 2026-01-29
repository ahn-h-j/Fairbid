package com.cos.fairbid.auth.application.port.out;

import com.cos.fairbid.user.domain.OAuthProvider;

/**
 * OAuth Provider에서 받은 사용자 정보를 통합하는 record
 * 각 Provider별 응답 형식이 다르므로 이 객체로 통일한다.
 *
 * @param email      사용자 이메일 (필수 - 모든 Provider에서 동의 필수)
 * @param providerId Provider 고유 사용자 식별자
 * @param provider   OAuth Provider 종류
 */
public record OAuthUserInfo(
        String email,
        String providerId,
        OAuthProvider provider
) {
}
