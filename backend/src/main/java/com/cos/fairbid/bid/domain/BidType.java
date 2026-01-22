package com.cos.fairbid.bid.domain;

import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.bid.domain.exception.InstantBuyException;
import com.cos.fairbid.bid.domain.exception.InvalidBidException;

/**
 * 입찰 유형
 * 각 유형별로 입찰 금액 계산 전략을 구현
 */
public enum BidType {

    /**
     * 원터치 입찰 - 현재가 + 입찰단위로 자동 입찰
     */
    ONE_TOUCH {
        @Override
        public Long calculateAmount(Long requestedAmount, Auction auction) {
            return auction.getMinBidAmount();
        }
    },

    /**
     * 금액 직접 지정 - 사용자가 입찰 금액을 직접 입력
     */
    DIRECT {
        @Override
        public Long calculateAmount(Long requestedAmount, Auction auction) {
            if (requestedAmount == null) {
                throw InvalidBidException.amountRequiredForDirectBid();
            }
            return requestedAmount;
        }
    },

    /**
     * 즉시 구매 - 판매자가 설정한 즉시 구매가로 입찰
     * 즉시 구매 발생 시 1시간 최종 입찰 기회 제공
     */
    INSTANT_BUY {
        @Override
        public Long calculateAmount(Long requestedAmount, Auction auction) {
            Long instantBuyPrice = auction.getInstantBuyPrice();
            if (instantBuyPrice == null) {
                throw InstantBuyException.notAvailable(auction.getId());
            }
            return instantBuyPrice;
        }
    };

    /**
     * 입찰 금액을 계산한다
     *
     * @param requestedAmount 요청된 입찰 금액 (DIRECT 시 필수)
     * @param auction         경매 정보
     * @return 최종 입찰 금액
     */
    public abstract Long calculateAmount(Long requestedAmount, Auction auction);
}
