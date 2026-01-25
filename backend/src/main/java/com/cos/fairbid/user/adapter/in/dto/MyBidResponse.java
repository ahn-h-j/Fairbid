package com.cos.fairbid.user.adapter.in.dto;

import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.user.application.port.in.GetMyBidsUseCase.MyBidItem;

import java.time.LocalDateTime;

/**
 * 내 입찰 경매 응답 DTO
 *
 * @param auctionId    경매 ID
 * @param title        제목
 * @param myHighestBid 내 최고 입찰가
 * @param currentPrice 현재가
 * @param status       경매 상태
 * @param createdAt    등록일
 */
public record MyBidResponse(
        Long auctionId,
        String title,
        Long myHighestBid,
        Long currentPrice,
        AuctionStatus status,
        LocalDateTime createdAt
) {
    /**
     * UseCase 결과에서 응답 DTO를 생성한다.
     */
    public static MyBidResponse from(MyBidItem item) {
        return new MyBidResponse(
                item.auctionId(),
                item.title(),
                item.myHighestBid(),
                item.currentPrice(),
                item.status(),
                item.createdAt()
        );
    }
}
