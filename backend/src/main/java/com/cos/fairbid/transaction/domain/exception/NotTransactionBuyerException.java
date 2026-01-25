package com.cos.fairbid.transaction.domain.exception;

import com.cos.fairbid.common.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * 거래의 구매자가 아닌 사용자가 결제를 시도할 때 발생하는 예외
 * HTTP 403 Forbidden에 매핑
 */
public class NotTransactionBuyerException extends DomainException {

    private NotTransactionBuyerException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.FORBIDDEN;
    }

    public static NotTransactionBuyerException of(Long transactionId, Long userId) {
        return new NotTransactionBuyerException(
                "NOT_TRANSACTION_BUYER",
                "해당 거래의 구매자가 아닙니다. 거래 ID: " + transactionId + ", 사용자 ID: " + userId
        );
    }
}
