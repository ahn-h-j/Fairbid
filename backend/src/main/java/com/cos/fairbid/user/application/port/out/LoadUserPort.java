package com.cos.fairbid.user.application.port.out;

import com.cos.fairbid.user.domain.OAuthProvider;
import com.cos.fairbid.user.domain.User;

import java.util.Optional;

/**
 * 사용자 조회 아웃바운드 포트
 * 영속성 계층에서 사용자를 조회하는 인터페이스를 정의한다.
 */
public interface LoadUserPort {

    /**
     * ID로 사용자를 조회한다.
     */
    Optional<User> findById(Long userId);

    /**
     * OAuth Provider와 Provider ID로 사용자를 조회한다.
     * 로그인 시 기존 사용자 여부를 판단하는 데 사용된다.
     */
    Optional<User> findByProviderAndProviderId(OAuthProvider provider, String providerId);

    /**
     * 닉네임 중복 여부를 확인한다.
     */
    boolean existsByNickname(String nickname);

    /**
     * 전화번호 중복 여부를 확인한다.
     */
    boolean existsByPhoneNumber(String phoneNumber);
}
