package com.cos.fairbid.admin.adapter.in.dto;

import com.cos.fairbid.user.domain.OAuthProvider;
import com.cos.fairbid.user.domain.User;
import com.cos.fairbid.user.domain.UserRole;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 관리자용 유저 응답 DTO
 */
@Builder
public record AdminUserResponse(
        Long id,
        String email,
        String nickname,
        String phoneNumber,
        OAuthProvider provider,
        UserRole role,
        int warningCount,
        boolean isActive,
        boolean isBlocked,
        boolean isOnboarded,
        LocalDateTime createdAt
) {
    /**
     * Domain → Response DTO 변환
     *
     * @param user 유저 도메인 객체
     * @return 관리자용 유저 응답 DTO
     */
    public static AdminUserResponse from(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .phoneNumber(maskPhoneNumber(user.getPhoneNumber()))
                .provider(user.getProvider())
                .role(user.getRole())
                .warningCount(user.getWarningCount())
                .isActive(user.isActive())
                .isBlocked(user.isBlocked())
                .isOnboarded(user.isOnboarded())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * 전화번호 마스킹 (010-****-1234)
     */
    private static String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 8) {
            return phoneNumber;
        }
        // 010-1234-5678 형식 가정
        return phoneNumber.substring(0, 4) + "****" + phoneNumber.substring(8);
    }
}
