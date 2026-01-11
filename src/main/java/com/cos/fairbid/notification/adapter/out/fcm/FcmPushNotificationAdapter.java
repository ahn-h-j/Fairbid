package com.cos.fairbid.notification.adapter.out.fcm;

import com.cos.fairbid.notification.application.port.out.PushNotificationPort;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * FCM Push 알림 어댑터
 * PushNotificationPort를 구현하여 실제 FCM 전송 수행
 *
 * TODO: User 도메인에서 FCM 토큰 관리 구현 필요
 * 현재는 로그로 대체
 */
@Slf4j
@Component
public class FcmPushNotificationAdapter implements PushNotificationPort {

    @Override
    public void sendWinningNotification(Long userId, Long auctionId, String auctionTitle, Long bidAmount) {
        String title = "축하합니다! 낙찰되었습니다 🎉";
        String body = String.format("[%s] %,d원에 낙찰되었습니다. 3시간 내에 결제해주세요.", auctionTitle, bidAmount);

        sendPushNotification(userId, title, body, "WINNING", auctionId);
    }

    @Override
    public void sendTransferNotification(Long userId, Long auctionId, String auctionTitle, Long bidAmount) {
        String title = "낙찰 기회가 생겼습니다!";
        String body = String.format("[%s] 2순위로 낙찰 권한이 승계되었습니다. 1시간 내에 결제해주세요.", auctionTitle);

        sendPushNotification(userId, title, body, "TRANSFER", auctionId);
    }

    @Override
    public void sendFailedAuctionNotification(Long sellerId, Long auctionId, String auctionTitle) {
        String title = "경매가 유찰되었습니다";
        String body = String.format("[%s] 경매가 유찰되었습니다. 재등록을 고려해보세요.", auctionTitle);

        sendPushNotification(sellerId, title, body, "FAILED", auctionId);
    }

    /**
     * FCM Push 알림 전송
     *
     * TODO: User 도메인에서 FCM 토큰 조회 기능 구현 필요
     * 현재는 로그로 대체
     */
    private void sendPushNotification(Long userId, String title, String body, String type, Long auctionId) {
        // Firebase 초기화 여부 확인
        if (FirebaseApp.getApps().isEmpty()) {
            log.info("[FCM Mock] userId={}, type={}, title={}, body={}", userId, type, title, body);
            return;
        }

        try {
            // TODO: User 도메인에서 FCM 토큰 조회
            // String fcmToken = userRepository.getFcmToken(userId);
            String fcmToken = null; // 임시

            if (fcmToken == null) {
                log.warn("FCM 토큰이 없어 Push 알림을 보낼 수 없습니다. userId={}", userId);
                log.info("[FCM Mock] userId={}, type={}, title={}, body={}", userId, type, title, body);
                return;
            }

            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putData("type", type)
                    .putData("auctionId", String.valueOf(auctionId))
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("FCM 전송 성공 - userId={}, messageId={}", userId, response);

        } catch (Exception e) {
            log.error("FCM 전송 실패 - userId={}", userId, e);
            // 실패해도 비즈니스 로직에 영향 주지 않음
        }
    }
}
