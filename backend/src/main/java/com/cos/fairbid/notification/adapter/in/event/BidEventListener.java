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
        log.info("Received BidPlacedEvent: auctionId={}, currentPrice={}, extended={}, nextMinBidPrice={}, bidIncrement={}, totalBidCount={}",
                event.getAuctionId(), event.getCurrentPrice(), event.isExtended(),
                event.getNextMinBidPrice(), event.getBidIncrement(), event.getTotalBidCount());

        // 이벤트를 WebSocket 메시지로 변환
        BidUpdateMessage message = BidUpdateMessage.from(
                event.getAuctionId(),
                event.getCurrentPrice(),
                event.getScheduledEndTime(),
                event.isExtended(),
                event.getNextMinBidPrice(),
                event.getBidIncrement(),
                event.getTotalBidCount(),
                event.getOccurredAt()
        );

        // 구독자들에게 브로드캐스트 (실패해도 다른 리스너/트랜잭션에 영향이 없도록 보호)
        try {
            auctionBroadcastPort.broadcastBidUpdate(message);
        } catch (Exception e) {
            log.error(
                    "Failed to broadcast BidUpdateMessage for auctionId={}, currentPrice={}, extended={}",
                    event.getAuctionId(),
                    event.getCurrentPrice(),
                    event.isExtended(),
                    e
            );
        }
    }
}
