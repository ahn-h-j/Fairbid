package com.cos.fairbid.auth.infrastructure.jwt;

import com.cos.fairbid.auth.domain.exception.TokenExpiredException;
import com.cos.fairbid.auth.domain.exception.TokenInvalidException;
import com.cos.fairbid.user.domain.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 토큰 생성 및 검증 컴포넌트
 *
 * - Access Token: userId, nickname, onboarded 클레임 포함 (30분)
 * - Refresh Token: userId 클레임만 포함 (2주)
 * - 서명 알고리즘: HMAC-SHA256
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private SecretKey secretKey;

    /**
     * Base64로 인코딩된 secret key를 SecretKey 객체로 변환한다.
     */
    @PostConstruct
    void init() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSecretKey());
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Access Token을 생성한다.
     * 클레임에 userId, nickname, onboarded 정보를 포함하여
     * 프론트엔드에서 추가 API 호출 없이 사용자 상태를 판단할 수 있도록 한다.
     *
     * @param user User 도메인 객체
     * @return Access Token 문자열
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessExpiration());

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("nickname", user.getNickname())
                .claim("onboarded", user.isOnboarded())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Refresh Token을 생성한다.
     * 최소한의 정보(userId)만 포함하여 탈취 시 위험을 최소화한다.
     *
     * @param user User 도메인 객체
     * @return Refresh Token 문자열
     */
    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getRefreshExpiration());

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 토큰을 파싱하여 Claims를 반환한다.
     * 만료 또는 유효하지 않은 토큰일 경우 적절한 예외를 발생시킨다.
     *
     * @param token JWT 토큰 문자열
     * @return Claims 객체
     * @throws TokenExpiredException  토큰 만료 시
     * @throws TokenInvalidException  토큰 무효 시
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT 토큰 만료: {}", e.getMessage());
            throw TokenExpiredException.accessToken();
        } catch (JwtException e) {
            log.debug("JWT 토큰 검증 실패: {}", e.getMessage());
            throw TokenInvalidException.malformed();
        }
    }

    /**
     * 토큰에서 사용자 ID를 추출한다.
     * subject 클레임을 Long으로 변환하여 반환한다.
     *
     * @param token JWT 토큰 문자열
     * @return 사용자 ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = validateToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * Refresh Token의 만료 시간을 초 단위로 반환한다.
     * Redis TTL 설정에 사용된다.
     *
     * @return Refresh Token 만료 시간 (초)
     */
    public long getRefreshExpirationSeconds() {
        return jwtProperties.getRefreshExpiration() / 1000;
    }
}
