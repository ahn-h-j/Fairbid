package com.cos.fairbid.user.adapter.in.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 닉네임 수정 요청 DTO
 *
 * @param nickname 새로운 닉네임 (2~20자)
 */
public record UpdateNicknameRequest(
        @NotBlank(message = "닉네임은 필수입니다")
        @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다")
        String nickname
) {
}
