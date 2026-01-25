package com.cos.fairbid.transaction.application.service;

import com.cos.fairbid.notification.application.port.out.PushNotificationPort;
import com.cos.fairbid.transaction.adapter.in.dto.PaymentResponse;
import com.cos.fairbid.transaction.application.port.in.PaymentUseCase;
import com.cos.fairbid.transaction.application.port.out.PaymentGatewayPort;
import com.cos.fairbid.transaction.application.port.out.TransactionRepositoryPort;
import com.cos.fairbid.transaction.domain.Transaction;
import com.cos.fairbid.transaction.domain.exception.AlreadyPaidException;
import com.cos.fairbid.transaction.domain.exception.NotTransactionBuyerException;
import com.cos.fairbid.transaction.domain.exception.PaymentExpiredException;
import com.cos.fairbid.transaction.domain.exception.TransactionNotFoundException;
import com.cos.fairbid.winning.application.port.out.WinningRepositoryPort;
import com.cos.fairbid.winning.domain.Winning;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 처리 서비스
 * PaymentUseCase 구현체로서 결제 비즈니스 로직을 수행한다
 *
 * 결제 플로우:
 * 1. 거래 유효성 검증 (존재, 구매자 확인, 중복 결제, 기한 만료)
 * 2. PG사(Mock) 결제 호출
 * 3. Transaction 결제 완료 처리
 * 4. Winning 결제 완료 처리
 * 5. 구매자/판매자 알림 발송
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService implements PaymentUseCase {

    private final TransactionRepositoryPort transactionRepositoryPort;
    private final WinningRepositoryPort winningRepositoryPort;
    private final PaymentGatewayPort paymentGatewayPort;
    private final PushNotificationPort pushNotificationPort;

    /**
     * 결제를 처리한다
     *
     * @param transactionId 거래 ID
     * @param userId        결제 요청 사용자 ID
     * @return 결제 처리 결과 응답
     * @throws TransactionNotFoundException   거래를 찾을 수 없는 경우
     * @throws NotTransactionBuyerException   구매자가 아닌 경우
     * @throws AlreadyPaidException           이미 결제 완료된 경우
     * @throws PaymentExpiredException        결제 기한이 만료된 경우
     */
    @Override
    public PaymentResponse processPayment(Long transactionId, Long userId) {
        log.info("결제 처리 시작 - 거래 ID: {}, 사용자 ID: {}", transactionId, userId);

        // 1. Transaction 조회
        Transaction transaction = transactionRepositoryPort.findById(transactionId)
                .orElseThrow(() -> TransactionNotFoundException.withId(transactionId));

        // 2. 구매자 확인
        if (!transaction.isBuyer(userId)) {
            throw NotTransactionBuyerException.of(transactionId, userId);
        }

        // 3. 이미 결제됨 확인
        if (!transaction.isAwaitingPayment()) {
            throw AlreadyPaidException.of(transactionId);
        }

        // 4. 기한 만료 확인
        if (transaction.isPaymentExpired()) {
            throw PaymentExpiredException.of(transactionId);
        }

        // 5. PG사 결제 호출 (Mock)
        paymentGatewayPort.processPayment(transactionId, transaction.getFinalPrice());

        // 6. Transaction 결제 완료 처리
        transaction.markAsPaid();
        Transaction savedTransaction = transactionRepositoryPort.save(transaction);

        // 7. Winning 조회 후 결제 완료 처리
        // 현재 구매자(buyerId)에 해당하는 PENDING_PAYMENT 상태의 Winning을 찾아 결제 완료 처리
        // (1순위 또는 2순위 승계 후 결제 모두 지원)
        winningRepositoryPort.findPendingByAuctionIdAndBidderId(transaction.getAuctionId(), transaction.getBuyerId())
                .ifPresent(winning -> {
                    winning.markAsPaid();
                    winningRepositoryPort.save(winning);
                });

        // 8. 알림 발송 (구매자 + 판매자)
        sendPaymentNotifications(savedTransaction);

        log.info("결제 처리 완료 - 거래 ID: {}, 금액: {}원", transactionId, transaction.getFinalPrice());
        return PaymentResponse.from(savedTransaction);
    }

    /**
     * 결제 완료 알림을 구매자와 판매자에게 발송한다
     *
     * @param transaction 결제 완료된 거래
     */
    private void sendPaymentNotifications(Transaction transaction) {
        try {
            // 구매자에게 결제 완료 알림
            pushNotificationPort.sendPaymentCompletedNotification(
                    transaction.getBuyerId(),
                    transaction.getAuctionId(),
                    transaction.getFinalPrice()
            );

            // 판매자에게 결제 완료 알림
            pushNotificationPort.sendPaymentCompletedNotification(
                    transaction.getSellerId(),
                    transaction.getAuctionId(),
                    transaction.getFinalPrice()
            );
        } catch (Exception e) {
            // 알림 실패가 결제 트랜잭션에 영향을 주지 않도록 예외를 로그만 남김
            log.warn("결제 완료 알림 발송 실패 - 거래 ID: {}, 원인: {}", transaction.getId(), e.getMessage());
        }
    }
}
