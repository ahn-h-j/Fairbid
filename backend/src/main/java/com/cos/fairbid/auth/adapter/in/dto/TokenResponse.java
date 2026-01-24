package com.cos.fairbid.auth.adapter.in.dto;

/**
 * Access Token 응답 DTO
 * 클라이언트에게 Access Token과 온보딩 상태를 전달한다.
 *
 * @param accessToken Access Token 문자열
 * @param onboarded   온보딩 완료 여부 (프론트에서 온보딩 페이지 리다이렉트 판단용)
 */
public record TokenResponse(
        String accessToken,
        boolean onboarded
) {
}
