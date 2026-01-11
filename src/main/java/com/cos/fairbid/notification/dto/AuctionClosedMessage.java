package com.cos.fairbid.notification.dto;

import java.time.LocalDateTime;

/**
 * 경매 종료 WebSocket 메시지
 */
public record AuctionClosedMessage(
        Long auctionId,
        String type,
        LocalDateTime closedAt
) {
    /**
     * 경매 종료 메시지 생성
     *
     * @param auctionId 경매 ID
     * @return 경매 종료 메시지
     */
    public static AuctionClosedMessage of(Long auctionId) {
        return new AuctionClosedMessage(
                auctionId,
                "AUCTION_CLOSED",
                LocalDateTime.now()
        );
    }
}
