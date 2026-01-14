package com.cos.fairbid.notification.adapter.in.event;

import com.cos.fairbid.bid.domain.event.BidPlacedEvent;
import com.cos.fairbid.notification.application.port.out.AuctionBroadcastPort;
import com.cos.fairbid.notification.dto.BidUpdateMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 입찰 이벤트 리스너
 * BidPlacedEvent를 구독하여 WebSocket으로 실시간 알림 전송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidEventListener {

    private final AuctionBroadcastPort auctionBroadcastPort;

    /**
     * 입찰 완료 이벤트 처리
     * 트랜잭션 커밋 후에만 실행되어 롤백 시 메시지 미전송 보장
     *
     * @param event 입찰 완료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBidPlacedEvent(BidPlacedEvent event) {
        log.debug("BidPlacedEvent 수신: auctionId={}, currentPrice={}", event.getAuctionId(), event.getCurrentPrice());

        try {
            auctionBroadcastPort.broadcastBidUpdate(BidUpdateMessage.from(event));
        } catch (Exception e) {
            log.error("BidUpdateMessage 브로드캐스트 실패: auctionId={}", event.getAuctionId(), e);
        }
    }
}
