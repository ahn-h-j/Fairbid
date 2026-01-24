package com.cos.fairbid.user.adapter.out.persistence.repository;

import com.cos.fairbid.user.adapter.out.persistence.entity.UserEntity;
import com.cos.fairbid.user.domain.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * User Spring Data JPA Repository
 */
public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    /**
     * OAuth Provider와 Provider ID로 사용자를 조회한다.
     * 로그인 시 기존 가입 여부를 판단하는 핵심 쿼리이다.
     */
    Optional<UserEntity> findByProviderAndProviderId(OAuthProvider provider, String providerId);

    /**
     * 닉네임 중복 여부를 확인한다.
     */
    boolean existsByNickname(String nickname);

    /**
     * 전화번호 중복 여부를 확인한다.
     */
    boolean existsByPhoneNumber(String phoneNumber);
}
