package com.cos.fairbid.transaction.domain.exception;

import com.cos.fairbid.common.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * 이미 결제 완료된 거래에 중복 결제 시도 시 발생하는 예외
 * HTTP 400 Bad Request에 매핑
 */
public class AlreadyPaidException extends DomainException {

    private AlreadyPaidException(String errorCode, String message) {
        super(errorCode, message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }

    public static AlreadyPaidException of(Long transactionId) {
        return new AlreadyPaidException(
                "ALREADY_PAID",
                "이미 결제가 완료된 거래입니다. 거래 ID: " + transactionId
        );
    }
}
