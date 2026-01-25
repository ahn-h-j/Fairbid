package com.cos.fairbid.transaction.domain.exception;

import com.cos.fairbid.common.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * 결제 기한이 만료되었을 때 발생하는 예외
 * HTTP 400 Bad Request에 매핑
 */
public class PaymentExpiredException extends DomainException {

    private PaymentExpiredException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    public static PaymentExpiredException of(Long transactionId) {
        return new PaymentExpiredException(
                "PAYMENT_EXPIRED",
                "결제 기한이 만료되었습니다. 거래 ID: " + transactionId
        );
    }
}
