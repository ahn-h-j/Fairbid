package com.cos.fairbid.auction.domain.exception;

import com.cos.fairbid.common.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * 경매 도메인 검증 예외
 * 경매 생성/수정 시 비즈니스 규칙 위반 시 발생
 * HTTP 400 Bad Request에 매핑
 */
public class InvalidAuctionException extends DomainException {

    private InvalidAuctionException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    /**
     * 즉시구매가가 시작가보다 낮거나 같을 때
     */
    public static InvalidAuctionException instantBuyPriceTooLow(Long startPrice, Long instantBuyPrice) {
        String message = String.format(
                "즉시구매가(%d)는 시작가(%d)보다 높아야 합니다.",
                instantBuyPrice, startPrice
        );
        return new InvalidAuctionException("INSTANT_BUY_PRICE_TOO_LOW", message);
    }
}
