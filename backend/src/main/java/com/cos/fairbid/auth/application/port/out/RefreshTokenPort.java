package com.cos.fairbid.auth.application.port.out;

import java.util.Optional;

/**
 * Refresh Token 저장소 아웃바운드 포트
 * Refresh Token의 저장, 조회, 삭제를 정의한다.
 * 구현체는 Redis를 사용하여 TTL 기반으로 관리한다.
 */
public interface RefreshTokenPort {

    /**
     * Refresh Token을 저장한다.
     * 기존 토큰이 있으면 덮어쓴다. (단일 세션 정책)
     *
     * @param userId       사용자 ID
     * @param refreshToken Refresh Token 문자열
     * @param ttlSeconds   만료 시간 (초)
     */
    void save(Long userId, String refreshToken, long ttlSeconds);

    /**
     * 사용자의 Refresh Token을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 저장된 Refresh Token (없으면 empty)
     */
    Optional<String> find(Long userId);

    /**
     * 사용자의 Refresh Token을 삭제한다.
     * 로그아웃 또는 토큰 재사용 감지 시 호출된다.
     *
     * @param userId 사용자 ID
     */
    void delete(Long userId);

    /**
     * 저장된 Refresh Token과 요청 토큰이 일치하는지 확인한다.
     * Token Rotation에서 재사용 감지에 사용된다.
     *
     * @param userId       사용자 ID
     * @param refreshToken 검증할 Refresh Token
     * @return 일치 여부
     */
    boolean matches(Long userId, String refreshToken);
}
