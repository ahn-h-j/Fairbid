package com.cos.fairbid.user.adapter.in.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 온보딩 요청 DTO
 *
 * @param nickname    닉네임 (2~20자)
 * @param phoneNumber 전화번호 (010-XXXX-XXXX 형식)
 */
public record OnboardingRequest(
        @NotBlank(message = "닉네임은 필수입니다")
        @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다")
        String nickname,

        @NotBlank(message = "전화번호는 필수입니다")
        @Pattern(regexp = "^010-\\d{4}-\\d{4}$", message = "전화번호 형식은 010-XXXX-XXXX여야 합니다")
        String phoneNumber
) {
}
