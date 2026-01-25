package com.cos.fairbid.user.application.port.in;

/**
 * 회원 탈퇴(비활성화) 유스케이스
 * Soft Delete 방식으로 isActive=false 처리하고 Refresh Token을 삭제한다.
 */
public interface DeactivateAccountUseCase {

    /**
     * 계정을 비활성화한다.
     *
     * @param userId 사용자 ID
     */
    void deactivate(Long userId);
}
