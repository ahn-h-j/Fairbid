package com.cos.fairbid.notification.adapter.out.fcm;

import com.cos.fairbid.notification.application.port.out.PushNotificationPort;
import com.cos.fairbid.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FCM Push 알림 어댑터
 * PushNotificationPort를 구현하여 FCM 전송 수행
 * 메시지 생성은 NotificationType에 위임, 전송은 FcmClient에 위임
 */
@Component
@RequiredArgsConstructor
public class FcmPushNotificationAdapter implements PushNotificationPort {

    private final FcmClient fcmClient;

    @Override
    public void sendWinningNotification(Long userId, Long auctionId, String auctionTitle, Long bidAmount) {
        NotificationType type = NotificationType.WINNING;
        fcmClient.send(userId, type.getTitle(), type.formatBody(auctionTitle, bidAmount), type, auctionId);
    }

    @Override
    public void sendTransferNotification(Long userId, Long auctionId, String auctionTitle, Long bidAmount) {
        NotificationType type = NotificationType.TRANSFER;
        fcmClient.send(userId, type.getTitle(), type.formatBody(auctionTitle, bidAmount), type, auctionId);
    }

    @Override
    public void sendFailedAuctionNotification(Long sellerId, Long auctionId, String auctionTitle) {
        NotificationType type = NotificationType.FAILED;
        fcmClient.send(sellerId, type.getTitle(), type.formatBody(auctionTitle, null), type, auctionId);
    }

    @Override
    public void sendPaymentCompletedNotification(Long userId, Long auctionId, Long amount) {
        NotificationType type = NotificationType.PAYMENT_COMPLETED;
        fcmClient.send(userId, type.getTitle(), type.formatBody(null, amount), type, auctionId);
    }

    @Override
    public void sendPaymentReminderNotification(Long buyerId, Long auctionId, String auctionTitle, Long amount) {
        NotificationType type = NotificationType.PAYMENT_REMINDER;
        fcmClient.send(buyerId, type.getTitle(), type.formatBody(auctionTitle, amount), type, auctionId);
    }
}
