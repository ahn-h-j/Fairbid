package com.cos.fairbid.auction.adapter.in.dto;

import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.auction.domain.Category;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 경매 응답 DTO
 */
@Builder
public record AuctionResponse(
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
        LocalDateTime createdAt
) {
    /**
     * Domain → Response DTO 변환
     */
    public static AuctionResponse from(Auction auction) {
        return AuctionResponse.builder()
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
                .build();
    }
}
