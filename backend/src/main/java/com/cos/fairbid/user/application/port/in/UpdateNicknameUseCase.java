package com.cos.fairbid.user.application.port.in;

/**
 * 닉네임 수정 유스케이스
 * 닉네임 수정 후 새로운 JWT를 발급한다. (JWT에 nickname 클레임 포함)
 */
public interface UpdateNicknameUseCase {

    /**
     * 닉네임을 수정한다.
     *
     * @param userId   사용자 ID
     * @param nickname 새 닉네임 (2~20자, UK)
     * @return 새로 발급된 토큰 결과
     */
    UpdateResult updateNickname(Long userId, String nickname);

    /**
     * 닉네임 수정 결과 record
     *
     * @param accessToken  새 Access Token (nickname 변경 반영)
     * @param refreshToken 새 Refresh Token
     */
    record UpdateResult(
            String accessToken,
            String refreshToken
    ) {
    }
}
