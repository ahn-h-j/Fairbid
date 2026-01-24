package com.cos.fairbid.auth.application.port.in;

/**
 * 로그아웃 유스케이스
 * Redis에서 Refresh Token을 삭제하여 세션을 무효화한다.
 */
public interface LogoutUseCase {

    /**
     * 로그아웃을 수행한다.
     * Redis에서 해당 사용자의 Refresh Token을 삭제한다.
     *
     * @param userId 로그아웃 대상 사용자 ID
     */
    void logout(Long userId);
}
