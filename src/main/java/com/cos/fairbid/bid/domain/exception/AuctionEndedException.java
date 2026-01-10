package com.cos.fairbid.bid.domain.exception;

import lombok.Getter;

/**
 * 경매가 이미 종료되었을 때 발생하는 예외
 * HTTP 400 Bad Request에 매핑
 */
@Getter
public class AuctionEndedException extends RuntimeException {

    private final String errorCode;

    private AuctionEndedException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 종료된 경매에 입찰 시도 시 발생
     *
     * @param auctionId 경매 ID
     * @return AuctionEndedException 인스턴스
     */
    public static AuctionEndedException forBid(Long auctionId) {
        String message = String.format("이미 종료된 경매입니다. (경매 ID: %d)", auctionId);
        return new AuctionEndedException("AUCTION_ENDED", message);
    }
}
