package com.cos.fairbid.transaction.domain;

import java.util.UUID;

/**
 * 결제 처리 결과를 나타내는 값 객체
 * PaymentGateway로부터 반환되는 결제 결과 정보를 담는다
 *
 * @param success    결제 성공 여부
 * @param paymentKey 결제 고유 키 (PG사에서 부여하는 결제 식별자)
 */
public record PaymentResult(boolean success, String paymentKey) {

    /**
     * 결제 성공 결과를 생성한다
     * paymentKey는 UUID 기반으로 자동 생성된다
     *
     * @param transactionId 거래 ID (paymentKey 접두사로 사용)
     * @return 성공 상태의 PaymentResult
     */
    public static PaymentResult success(Long transactionId) {
        String key = "PAY-" + transactionId + "-" + UUID.randomUUID().toString().substring(0, 8);
        return new PaymentResult(true, key);
    }

    /**
     * 결제 실패 결과를 생성한다
     *
     * @return 실패 상태의 PaymentResult
     */
    public static PaymentResult failure() {
        return new PaymentResult(false, null);
    }
}
