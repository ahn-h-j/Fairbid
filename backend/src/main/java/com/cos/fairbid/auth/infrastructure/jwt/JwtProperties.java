package com.cos.fairbid.auth.infrastructure.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 관련 설정 프로퍼티
 * application.yml의 jwt.* 프로퍼티를 바인딩한다.
 *
 * - secret-key: HMAC-SHA256 서명에 사용할 비밀 키 (Base64 인코딩)
 * - access-expiration: Access Token 만료 시간 (밀리초, 기본 30분)
 * - refresh-expiration: Refresh Token 만료 시간 (밀리초, 기본 2주)
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT 서명에 사용할 비밀 키 (Base64 인코딩된 문자열)
     * 최소 256비트(32바이트) 이상이어야 한다.
     */
    private String secretKey;

    /**
     * Access Token 만료 시간 (밀리초)
     * 기본값: 1,800,000ms (30분)
     */
    private long accessExpiration = 1_800_000;

    /**
     * Refresh Token 만료 시간 (밀리초)
     * 기본값: 1,209,600,000ms (2주)
     */
    private long refreshExpiration = 1_209_600_000;
}
