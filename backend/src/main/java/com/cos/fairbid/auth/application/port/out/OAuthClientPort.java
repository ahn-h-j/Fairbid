package com.cos.fairbid.auth.application.port.out;

import com.cos.fairbid.auth.adapter.out.oauth.OAuthUserInfo;
import com.cos.fairbid.user.domain.OAuthProvider;

/**
 * OAuth 클라이언트 아웃바운드 포트
 * Authorization Code를 사용하여 OAuth Provider에서 사용자 정보를 조회한다.
 */
public interface OAuthClientPort {

    /**
     * Authorization Code로 사용자 정보를 조회한다.
     * 내부적으로 Code → Token 교환 → UserInfo 조회를 수행한다.
     *
     * @param provider OAuth Provider 종류
     * @param code     Authorization Code
     * @return OAuth 사용자 정보
     */
    OAuthUserInfo getUserInfo(OAuthProvider provider, String code);
}
