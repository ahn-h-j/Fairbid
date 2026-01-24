package com.cos.fairbid.auth.adapter.out.oauth;

import com.cos.fairbid.auth.domain.exception.OAuthEmailRequiredException;
import com.cos.fairbid.user.domain.OAuthProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 네이버 OAuth2 클라이언트
 *
 * 인증 흐름:
 * 1. Authorization Code → Access Token 교환 (POST https://nid.naver.com/oauth2.0/token)
 * 2. Access Token → 사용자 정보 조회 (GET https://openapi.naver.com/v1/nid/me)
 *
 * 네이버 응답 구조:
 * {
 *   "response": {
 *     "id": "abc123",
 *     "email": "user@naver.com"
 *   }
 * }
 */
@Slf4j
@Component
public class NaverOAuthClient {

    private static final String TOKEN_URL = "https://nid.naver.com/oauth2.0/token";
    private static final String USER_INFO_URL = "https://openapi.naver.com/v1/nid/me";

    private final RestClient restClient;
    private final OAuthProperties oAuthProperties;

    public NaverOAuthClient(OAuthProperties oAuthProperties) {
        this.restClient = RestClient.create();
        this.oAuthProperties = oAuthProperties;
    }

    /**
     * Authorization Code로 네이버 사용자 정보를 조회한다.
     *
     * @param code Authorization Code
     * @return OAuth 사용자 정보
     */
    @SuppressWarnings("unchecked")
    public OAuthUserInfo getUserInfo(String code) {
        // 1. Code → Access Token 교환
        String accessToken = getAccessToken(code);

        // 2. Access Token → 사용자 정보 조회
        Map<String, Object> response = restClient.get()
                .uri(USER_INFO_URL)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);

        // 3. 응답에서 이메일, providerId 추출 (네이버는 response 필드 안에 위치)
        Map<String, Object> naverResponse = (Map<String, Object>) response.get("response");
        String providerId = (String) naverResponse.get("id");
        String email = (String) naverResponse.get("email");

        if (email == null || email.isBlank()) {
            throw OAuthEmailRequiredException.from("NAVER");
        }

        log.debug("네이버 로그인 성공: providerId={}, email={}", providerId, email);
        return new OAuthUserInfo(email, providerId, OAuthProvider.NAVER);
    }

    /**
     * Authorization Code를 Access Token으로 교환한다.
     */
    @SuppressWarnings("unchecked")
    private String getAccessToken(String code) {
        OAuthProperties.Provider naver = oAuthProperties.getNaver();

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", naver.getClientId());
        params.add("client_secret", naver.getClientSecret());
        params.add("redirect_uri", naver.getRedirectUri());
        params.add("code", code);

        Map<String, Object> response = restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(params)
                .retrieve()
                .body(Map.class);

        return (String) response.get("access_token");
    }
}
