package com.cos.fairbid.user.adapter.in.dto;

/**
 * 닉네임 중복 확인 응답 DTO
 *
 * @param available 사용 가능 여부 (true: 사용 가능, false: 이미 사용 중)
 */
public record NicknameCheckResponse(
        boolean available
) {
}
