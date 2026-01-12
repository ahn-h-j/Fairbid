package com.cos.fairbid.winning.adapter.in.scheduler;

import com.cos.fairbid.winning.application.port.in.ProcessNoShowUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 결제 만료 감시 스케줄러
 * 주기적으로 결제 기한이 만료된 낙찰 건을 처리한다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutScheduler {

    private final ProcessNoShowUseCase processNoShowUseCase;

    /**
     * 1분마다 실행되어 결제 기한 만료 건을 처리한다
     * fixedDelay = 60000ms (1분) - 이전 작업 완료 후 1분 대기
     */
    @Scheduled(fixedDelay = 60000)
    public void checkPaymentTimeouts() {
        try {
            processNoShowUseCase.processExpiredPayments();
        } catch (Exception e) {
            log.error("결제 만료 감시 스케줄러 실행 중 오류 발생", e);
        }
    }
}
