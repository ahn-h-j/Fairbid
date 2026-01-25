package com.cos.fairbid.user.application.port.in;

/**
 * 닉네임 중복 확인 유스케이스
 * 비인증 상태에서도 호출 가능하다.
 */
public interface CheckNicknameUseCase {

    /**
     * 닉네임 사용 가능 여부를 확인한다.
     *
     * @param nickname 확인할 닉네임
     * @return 사용 가능 여부 (true: 사용 가능)
     */
    boolean isAvailable(String nickname);
}
