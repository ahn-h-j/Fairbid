package com.cos.fairbid.notification.adapter.out.websocket;

import com.cos.fairbid.notification.application.port.out.AuctionBroadcastPort;
import com.cos.fairbid.notification.dto.BidUpdateMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * WebSocket을 통한 경매 브로드캐스트 어댑터
 * AuctionBroadcastPort를 구현하여 실제 WebSocket 전송 수행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketBroadcastAdapter implements AuctionBroadcastPort {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 토픽 경로 패턴
     * /topic/auctions/{auctionId}
     */
    private static final String AUCTION_TOPIC_FORMAT = "/topic/auctions/%d";

    /**
     * 입찰 업데이트 메시지를 해당 경매 구독자들에게 브로드캐스트
     *
     * @param message 전송할 입찰 업데이트 메시지
     */
    @Override
    public void broadcastBidUpdate(BidUpdateMessage message) {
        String destination = String.format(AUCTION_TOPIC_FORMAT, message.auctionId());

        log.info("Broadcasting bid update to {}: currentPrice={}, extended={}",
                destination, message.currentPrice(), message.extended());

        messagingTemplate.convertAndSend(destination, message);
    }
}
