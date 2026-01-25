package com.cos.fairbid.user.application.port.in;

/**
 * 온보딩 완료 유스케이스
 * 닉네임과 전화번호를 등록하여 온보딩을 완료한다.
 * 완료 후 새로운 JWT(onboarded=true)를 발급한다.
 */
public interface CompleteOnboardingUseCase {

    /**
     * 온보딩을 완료한다.
     *
     * @param userId      사용자 ID
     * @param nickname    닉네임 (2~20자, UK)
     * @param phoneNumber 전화번호 (010-XXXX-XXXX, UK)
     * @return 새로 발급된 토큰 결과
     */
    OnboardingResult completeOnboarding(Long userId, String nickname, String phoneNumber);

    /**
     * 온보딩 결과 record
     *
     * @param accessToken  새 Access Token (onboarded=true 포함)
     * @param refreshToken 새 Refresh Token
     */
    record OnboardingResult(
            String accessToken,
            String refreshToken
    ) {
    }
}
