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
}
