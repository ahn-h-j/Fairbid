package com.cos.fairbid.transaction.application.port.out;

import com.cos.fairbid.transaction.domain.PaymentResult;

/**
 * 결제 게이트웨이 아웃바운드 포트
 * 외부 PG사와의 결제 처리 통신을 위한 인터페이스
 */
public interface PaymentGatewayPort {

    /**
     * 결제를 처리한다
     *
     * @param transactionId 거래 ID
     * @param amount        결제 금액
     * @return 결제 처리 결과 (성공 여부 + 결제 키)
     */
    PaymentResult processPayment(Long transactionId, Long amount);
}
