package com.cos.fairbid.winning.adapter.in.scheduler;

import com.cos.fairbid.auction.application.port.out.AuctionRepositoryPort;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.notification.application.port.out.PushNotificationPort;
import com.cos.fairbid.transaction.application.port.out.TransactionRepositoryPort;
import com.cos.fairbid.transaction.domain.Transaction;
import com.cos.fairbid.winning.application.port.in.ProcessNoShowUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 결제 만료 감시 스케줄러
 * 주기적으로 결제 기한이 만료된 낙찰 건을 처리하고,
 * 결제 마감 임박 시 리마인더 알림을 발송한다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutScheduler {

    private final ProcessNoShowUseCase processNoShowUseCase;
    private final TransactionRepositoryPort transactionRepositoryPort;
    private final PushNotificationPort pushNotificationPort;
    private final AuctionRepositoryPort auctionRepositoryPort;

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

    /**
     * 1분마다 실행되어 결제 마감 임박 리마인더를 발송한다
     * 결제 마감 1시간 전에 아직 리마인더를 받지 않은 구매자에게 알림 발송
     * fixedDelay = 60000ms (1분) - 이전 작업 완료 후 1분 대기
     */
    @Scheduled(fixedDelay = 60000)
    public void sendPaymentReminders() {
        try {
            // 리마인더 발송 대상 조회
            List<Transaction> reminderTargets = transactionRepositoryPort.findReminderTargets();

            if (reminderTargets.isEmpty()) {
                return;
            }

            log.info("결제 리마인더 발송 대상: {}건", reminderTargets.size());

            for (Transaction transaction : reminderTargets) {
                try {
                    // 경매 정보 조회하여 제목 획득
                    Auction auction = auctionRepositoryPort.findById(transaction.getAuctionId())
                            .orElse(null);

                    String auctionTitle = (auction != null) ? auction.getTitle() : "경매";

                    // 리마인더 알림 발송
                    pushNotificationPort.sendPaymentReminderNotification(
                            transaction.getBuyerId(),
                            transaction.getAuctionId(),
                            auctionTitle,
                            transaction.getFinalPrice()
                    );

                    // 리마인더 발송 완료 표시
                    transaction.markReminderSent();
                    transactionRepositoryPort.save(transaction);

                    log.debug("결제 리마인더 발송 완료 - auctionId: {}, buyerId: {}",
                            transaction.getAuctionId(), transaction.getBuyerId());
                } catch (Exception e) {
                    log.error("결제 리마인더 발송 실패 - auctionId: {}, buyerId: {}",
                            transaction.getAuctionId(), transaction.getBuyerId(), e);
                }
            }
        } catch (Exception e) {
            log.error("결제 리마인더 스케줄러 실행 중 오류 발생", e);
        }
    }
}
