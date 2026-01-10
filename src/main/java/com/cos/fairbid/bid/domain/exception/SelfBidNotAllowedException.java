package com.cos.fairbid.bid.domain.exception;

import lombok.Getter;

/**
 * 본인이 등록한 경매에 입찰 시도 시 발생하는 예외
 * HTTP 403 Forbidden에 매핑
 */
@Getter
public class SelfBidNotAllowedException extends RuntimeException {

    private final String errorCode;

    private SelfBidNotAllowedException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 판매자가 본인 경매에 입찰 시도 시
     *
     * @param auctionId 경매 ID
     * @param sellerId  판매자 ID
     * @return SelfBidNotAllowedException 인스턴스
     */
    public static SelfBidNotAllowedException forAuction(Long auctionId, Long sellerId) {
        String message = String.format(
                "본인이 등록한 경매에는 입찰할 수 없습니다. (경매 ID: %d, 판매자 ID: %d)",
                auctionId, sellerId
        );
        return new SelfBidNotAllowedException("SELF_BID_NOT_ALLOWED", message);
    }
}
