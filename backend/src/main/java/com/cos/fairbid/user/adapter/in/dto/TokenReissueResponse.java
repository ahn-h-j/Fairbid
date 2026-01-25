package com.cos.fairbid.user.adapter.in.dto;

/**
 * 토큰 재발급 응답 DTO
 * 온보딩 완료 또는 닉네임 수정 시 새로운 Access Token을 반환한다.
 * Refresh Token은 HttpOnly 쿠키로 설정되므로 응답 본문에는 Access Token만 포함한다.
 *
 * @param accessToken 새로 발급된 Access Token
 */
public record TokenReissueResponse(
        String accessToken
) {
}
