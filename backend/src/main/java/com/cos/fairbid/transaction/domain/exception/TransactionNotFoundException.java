package com.cos.fairbid.transaction.domain.exception;

import com.cos.fairbid.common.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * 거래 정보를 찾을 수 없을 때 발생하는 예외
 * HTTP 404 Not Found에 매핑
 */
public class TransactionNotFoundException extends DomainException {

    private TransactionNotFoundException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }

    public static TransactionNotFoundException withId(Long id) {
        return new TransactionNotFoundException(
                "TRANSACTION_NOT_FOUND",
                "거래 정보를 찾을 수 없습니다. ID: " + id
        );
    }

    public static TransactionNotFoundException withAuctionId(Long auctionId) {
        return new TransactionNotFoundException(
                "TRANSACTION_NOT_FOUND",
                "해당 경매의 거래 정보를 찾을 수 없습니다. 경매 ID: " + auctionId
        );
    }
}
