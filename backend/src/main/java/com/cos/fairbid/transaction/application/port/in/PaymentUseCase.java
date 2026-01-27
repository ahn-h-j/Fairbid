package com.cos.fairbid.transaction.application.port.in;

import com.cos.fairbid.transaction.adapter.in.dto.PaymentResponse;

/**
 * 결제 처리 유스케이스 인바운드 포트
 * 컨트롤러에서 결제 요청을 처리하기 위한 인터페이스
 */
public interface PaymentUseCase {

    /**
     * 결제를 처리한다
     * 거래 유효성 검증, PG사 결제 호출, 상태 변경, 알림 발송을 포함한다
     *
     * @param transactionId 거래 ID
     * @param userId        결제 요청 사용자 ID (구매자 확인용)
     * @return 결제 처리 결과 응답
     */
    PaymentResponse processPayment(Long transactionId, Long userId);
}
