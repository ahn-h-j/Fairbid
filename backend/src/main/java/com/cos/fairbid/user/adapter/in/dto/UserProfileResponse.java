package com.cos.fairbid.user.adapter.in.dto;

import com.cos.fairbid.user.domain.OAuthProvider;
import com.cos.fairbid.user.domain.User;

/**
 * 사용자 프로필 응답 DTO
 *
 * @param email        이메일
 * @param nickname     닉네임 (온보딩 전이면 null)
 * @param phoneNumber  전화번호 (온보딩 전이면 null)
 * @param warningCount 경고 횟수
 * @param provider     OAuth Provider (KAKAO, NAVER, GOOGLE)
 */
public record UserProfileResponse(
        String email,
        String nickname,
        String phoneNumber,
        int warningCount,
        OAuthProvider provider
) {
    /**
     * User 도메인 객체에서 응답 DTO를 생성한다.
     */
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getEmail(),
                user.getNickname(),
                user.getPhoneNumber(),
                user.getWarningCount(),
                user.getProvider()
        );
    }
}
