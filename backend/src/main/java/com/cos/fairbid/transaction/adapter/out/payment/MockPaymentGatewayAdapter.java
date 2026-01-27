package com.cos.fairbid.transaction.adapter.out.payment;

import com.cos.fairbid.transaction.application.port.out.PaymentGatewayPort;
import com.cos.fairbid.transaction.domain.PaymentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock 결제 게이트웨이 어댑터
 * 실제 PG사 연동 없이 결제 성공을 시뮬레이션한다
 * 1~2초의 랜덤 딜레이를 추가하여 실제 결제 처리 시간을 모방한다
 */
@Slf4j
@Component
public class MockPaymentGatewayAdapter implements PaymentGatewayPort {

    /** 최소 처리 시간 (밀리초) */
    private static final long MIN_DELAY_MS = 1000L;

    /** 최대 처리 시간 (밀리초) */
    private static final long MAX_DELAY_MS = 2000L;

    /**
     * 결제를 처리한다 (Mock)
     * 1~2초 랜덤 딜레이 후 무조건 성공을 반환한다
     *
     * @param transactionId 거래 ID
     * @param amount        결제 금액
     * @return 항상 성공 상태의 PaymentResult
     */
    @Override
    public PaymentResult processPayment(Long transactionId, Long amount) {
        log.info("[MockPayment] 결제 처리 시작 - 거래 ID: {}, 금액: {}원", transactionId, amount);

        // 1~2초 랜덤 딜레이로 실제 PG사 처리 시간 시뮬레이션
        long delayMs = ThreadLocalRandom.current().nextLong(MIN_DELAY_MS, MAX_DELAY_MS + 1);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[MockPayment] 결제 처리 중 인터럽트 발생 - 거래 ID: {}", transactionId);
        }

        PaymentResult result = PaymentResult.success(transactionId);
        log.info("[MockPayment] 결제 처리 완료 - 거래 ID: {}, paymentKey: {}, 소요시간: {}ms",
                transactionId, result.paymentKey(), delayMs);

        return result;
    }
}
