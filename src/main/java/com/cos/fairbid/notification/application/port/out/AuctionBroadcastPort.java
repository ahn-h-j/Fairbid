package com.cos.fairbid.notification.application.port.out;

import com.cos.fairbid.notification.dto.BidUpdateMessage;

/**
 * 경매 관련 실시간 브로드캐스트를 위한 아웃바운드 포트
 * WebSocket 등 실제 전송 기술은 Adapter에서 구현
 */
public interface AuctionBroadcastPort {

    /**
     * 입찰 업데이트 메시지를 해당 경매 구독자들에게 브로드캐스트
     *
     * @param message 전송할 입찰 업데이트 메시지
     */
    void broadcastBidUpdate(BidUpdateMessage message);

    /**
     * 경매 종료 메시지를 해당 경매 구독자들에게 브로드캐스트
     * 클라이언트는 이 메시지를 받으면 UI를 즉시 차단해야 함
     *
     * @param auctionId 종료된 경매 ID
     */
    void broadcastAuctionClosed(Long auctionId);
}
