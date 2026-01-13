package com.cos.fairbid.bid.domain.exception;

import lombok.Getter;

/**
 * 입찰 요청이 유효하지 않을 때 발생하는 예외
 * HTTP 400 Bad Request에 매핑
 */
@Getter
public class InvalidBidException extends RuntimeException {

    private final String errorCode;

    private InvalidBidException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * DIRECT 입찰 시 금액이 누락된 경우
     *
     * @return InvalidBidException 인스턴스
     */
    public static InvalidBidException amountRequiredForDirectBid() {
        return new InvalidBidException(
                "AMOUNT_REQUIRED",
                "금액 직접 지정 입찰 시 입찰 금액은 필수입니다."
        );
    }

    /**
     * 입찰자 ID가 누락된 경우
     *
     * @return InvalidBidException 인스턴스
     */
    public static InvalidBidException bidderIdRequired() {
        return new InvalidBidException(
                "BIDDER_ID_REQUIRED",
                "입찰자 ID는 필수입니다."
        );
    }
}
