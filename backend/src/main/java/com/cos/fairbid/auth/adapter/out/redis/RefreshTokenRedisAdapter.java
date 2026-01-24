package com.cos.fairbid.auth.adapter.out.redis;

import com.cos.fairbid.auth.application.port.out.RefreshTokenPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Refresh Token Redis 어댑터
 * Redis를 사용하여 Refresh Token을 관리한다.
 *
 * - Key 형식: refresh:{userId}
 * - Value: Refresh Token 문자열
 * - TTL: Refresh Token 만료 시간과 동일 (2주)
 *
 * 단일 세션 정책: 사용자당 하나의 Refresh Token만 저장된다.
 * 새로운 로그인 시 기존 토큰을 덮어쓰므로 이전 기기는 자동 로그아웃된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenRedisAdapter implements RefreshTokenPort {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(Long userId, String refreshToken, long ttlSeconds) {
        String key = generateKey(userId);
        redisTemplate.opsForValue().set(key, refreshToken, ttlSeconds, TimeUnit.SECONDS);
        log.debug("Refresh Token 저장 완료: userId={}", userId);
    }

    @Override
    public Optional<String> find(Long userId) {
        String key = generateKey(userId);
        String token = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(token);
    }

    @Override
    public void delete(Long userId) {
        String key = generateKey(userId);
        redisTemplate.delete(key);
        log.debug("Refresh Token 삭제 완료: userId={}", userId);
    }

    @Override
    public boolean matches(Long userId, String refreshToken) {
        return find(userId)
                .map(stored -> stored.equals(refreshToken))
                .orElse(false);
    }

    /**
     * Redis Key를 생성한다.
     *
     * @param userId 사용자 ID
     * @return "refresh:{userId}" 형식의 키
     */
    private String generateKey(Long userId) {
        return KEY_PREFIX + userId;
    }
}
