package com.cos.fairbid.user.application.port.in;

import com.cos.fairbid.user.domain.User;

/**
 * 내 프로필 조회 유스케이스
 */
public interface GetMyProfileUseCase {

    /**
     * 내 프로필 정보를 조회한다.
     *
     * @param userId 사용자 ID
     * @return User 도메인 객체
     */
    User getMyProfile(Long userId);
}
