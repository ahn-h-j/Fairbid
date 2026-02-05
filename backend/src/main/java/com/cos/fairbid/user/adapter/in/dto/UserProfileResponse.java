package com.cos.fairbid.user.adapter.in.dto;

import com.cos.fairbid.user.domain.OAuthProvider;
import com.cos.fairbid.user.domain.User;

/**
 * 사용자 프로필 응답 DTO
 *
 * @param email           이메일
 * @param nickname        닉네임 (온보딩 전이면 null)
 * @param phoneNumber     전화번호 (온보딩 전이면 null)
 * @param warningCount    경고 횟수
 * @param provider        OAuth Provider (KAKAO, NAVER, GOOGLE)
 * @param stats           거래 통계 (판매/구매 완료 수, 총 거래 금액)
 * @param shippingAddress 저장된 배송지 정보
 */
public record UserProfileResponse(
        String email,
        String nickname,
        String phoneNumber,
        int warningCount,
        OAuthProvider provider,
        TradeStats stats,
        ShippingAddress shippingAddress
) {
    /**
     * 거래 통계 DTO
     *
     * @param totalSales          완료된 판매 수
     * @param totalPurchases      완료된 구매 수
     * @param totalSalesAmount    총 판매 금액
     * @param totalPurchaseAmount 총 구매 금액
     */
    public record TradeStats(
            int totalSales,
            int totalPurchases,
            long totalSalesAmount,
            long totalPurchaseAmount
    ) {
    }

    /**
     * 배송지 정보 DTO
     */
    public record ShippingAddress(
            String recipientName,
            String recipientPhone,
            String postalCode,
            String address,
            String addressDetail
    ) {
    }

    /**
     * User 도메인 객체에서 응답 DTO를 생성한다. (stats 없이)
     */
    public static UserProfileResponse from(User user) {
        return from(user, null);
    }

    /**
     * User 도메인 객체와 거래 통계로 응답 DTO를 생성한다.
     */
    public static UserProfileResponse from(User user, TradeStats stats) {
        ShippingAddress shipping = null;
        if (user.hasShippingAddress()) {
            shipping = new ShippingAddress(
                    user.getShippingRecipientName(),
                    user.getShippingPhone(),
                    user.getShippingPostalCode(),
                    user.getShippingAddress(),
                    user.getShippingAddressDetail()
            );
        }

        return new UserProfileResponse(
                user.getEmail(),
                user.getNickname(),
                user.getPhoneNumber(),
                user.getWarningCount(),
                user.getProvider(),
                stats,
                shipping
        );
    }
}
