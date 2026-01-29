package com.cos.fairbid.user.adapter.out.persistence;

import com.cos.fairbid.user.adapter.out.persistence.mapper.UserMapper;
import com.cos.fairbid.user.adapter.out.persistence.repository.UserJpaRepository;
import com.cos.fairbid.user.application.port.out.LoadUserPort;
import com.cos.fairbid.user.application.port.out.SaveUserPort;
import com.cos.fairbid.user.domain.OAuthProvider;
import com.cos.fairbid.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * User 영속성 어댑터
 * LoadUserPort, SaveUserPort를 구현하여 JPA를 통해 User를 영속화한다.
 */
@Repository
@RequiredArgsConstructor
public class UserPersistenceAdapter implements LoadUserPort, SaveUserPort {

    private final UserJpaRepository userJpaRepository;
    private final UserMapper userMapper;

    @Override
    public Optional<User> findById(Long userId) {
        return userJpaRepository.findById(userId)
                .map(userMapper::toDomain);
    }

    @Override
    public Optional<User> findByProviderAndProviderId(OAuthProvider provider, String providerId) {
        return userJpaRepository.findByProviderAndProviderId(provider, providerId)
                .map(userMapper::toDomain);
    }

    @Override
    public boolean existsByNickname(String nickname) {
        return userJpaRepository.existsByNickname(nickname);
    }

    @Override
    public boolean existsByPhoneNumber(String phoneNumber) {
        return userJpaRepository.existsByPhoneNumber(phoneNumber);
    }

    @Override
    public User save(User user) {
        var entity = userMapper.toEntity(user);
        var savedEntity = userJpaRepository.save(entity);
        return userMapper.toDomain(savedEntity);
    }

    @Override
    public Page<User> findAll(String keyword, Pageable pageable) {
        return userJpaRepository.findAllByKeyword(keyword, pageable)
                .map(userMapper::toDomain);
    }
}
