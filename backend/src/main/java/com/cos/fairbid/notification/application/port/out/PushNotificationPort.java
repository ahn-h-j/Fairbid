package com.cos.fairbid.notification.application.port.out;

/**
 * Push 알림 발송 아웃바운드 포트
 */
public interface PushNotificationPort {

    /**
     * 낙찰 알림을 발송한다
     *
     * @param userId      사용자 ID
     * @param auctionId   경매 ID
     * @param auctionTitle 경매 제목
     * @param bidAmount   낙찰 금액
     */
    void sendWinningNotification(Long userId, Long auctionId, String auctionTitle, Long bidAmount);

    /**
     * 2순위 승계 알림을 발송한다
     *
     * @param userId      사용자 ID
     * @param auctionId   경매 ID
     * @param auctionTitle 경매 제목
     * @param bidAmount   입찰 금액
     */
    void sendTransferNotification(Long userId, Long auctionId, String auctionTitle, Long bidAmount);

    /**
     * 유찰 알림을 발송한다 (판매자에게)
     *
     * @param sellerId     판매자 ID
     * @param auctionId    경매 ID
     * @param auctionTitle 경매 제목
     */
    void sendFailedAuctionNotification(Long sellerId, Long auctionId, String auctionTitle);

    /**
     * 결제 완료 알림을 발송한다
     *
     * @param userId    알림 수신자 ID (구매자 또는 판매자)
     * @param auctionId 경매 ID
     * @param amount    결제 금액
     */
    void sendPaymentCompletedNotification(Long userId, Long auctionId, Long amount);

    /**
     * 결제 리마인더 알림을 발송한다
     * 결제 마감 1시간 전에 구매자에게 발송
     *
     * @param buyerId      구매자 ID
     * @param auctionId    경매 ID
     * @param auctionTitle 경매 제목
     * @param amount       결제 금액
     */
    void sendPaymentReminderNotification(Long buyerId, Long auctionId, String auctionTitle, Long amount);
}
