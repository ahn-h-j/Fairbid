package com.cos.fairbid.auction.adapter.in.dto;

import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.auction.domain.Category;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 경매 응답 DTO
 * 기본 경매 정보 + 계산된 비즈니스 필드 포함
 */
@Builder
public record AuctionResponse(
        // 기본 정보
        Long id,
        Long sellerId,
        String title,
        String description,
        Category category,
        Long startPrice,
        Long currentPrice,
        Long instantBuyPrice,
        Long bidIncrement,
        LocalDateTime scheduledEndTime,
        Integer extensionCount,
        Integer totalBidCount,
        AuctionStatus status,
        List<String> imageUrls,
        LocalDateTime createdAt,

        // 계산된 비즈니스 필드
        boolean instantBuyEnabled,  // 즉시 구매 버튼 활성화 여부
        Long nextMinBidPrice,       // 다음 입찰 가능 최소 금액
        boolean editable            // 수정 가능 여부
) {
    /**
     * Domain → Response DTO 변환
     * 도메인 객체의 비즈니스 로직 메서드를 호출하여 계산된 필드 포함
     *
     * @param auction 경매 도메인 객체
     * @return 경매 응답 DTO
     */
    public static AuctionResponse from(Auction auction) {
        return AuctionResponse.builder()
                // 기본 정보
                .id(auction.getId())
                .sellerId(auction.getSellerId())
                .title(auction.getTitle())
                .description(auction.getDescription())
                .category(auction.getCategory())
                .startPrice(auction.getStartPrice())
                .currentPrice(auction.getCurrentPrice())
                .instantBuyPrice(auction.getInstantBuyPrice())
                .bidIncrement(auction.getBidIncrement())
                .scheduledEndTime(auction.getScheduledEndTime())
                .extensionCount(auction.getExtensionCount())
                .totalBidCount(auction.getTotalBidCount())
                .status(auction.getStatus())
                .imageUrls(auction.getImageUrls())
                .createdAt(auction.getCreatedAt())
                // 계산된 비즈니스 필드
                .instantBuyEnabled(auction.isInstantBuyEnabled())
                .nextMinBidPrice(auction.getNextMinBidPrice())
                .editable(auction.isEditable())
                .build();
    }
}
