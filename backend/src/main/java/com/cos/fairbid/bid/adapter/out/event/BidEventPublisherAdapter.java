package com.cos.fairbid.bid.adapter.out.event;

import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.bid.application.port.out.BidEventPublisher;
import com.cos.fairbid.bid.domain.event.BidPlacedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 입찰 이벤트 발행 어댑터
 * BidEventPublisher 포트 구현체
 */
@Component
@RequiredArgsConstructor
public class BidEventPublisherAdapter implements BidEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 입찰 완료 이벤트를 발행한다
     * 실시간 UI 업데이트용 (현재가, 종료시간, 다음 입찰가, 입찰 단위, 총 입찰수)
     *
     * @param auction  경매 도메인 객체
     * @param extended 연장 여부
     */
    @Override
    public void publishBidPlaced(Auction auction, boolean extended) {
        BidPlacedEvent event = BidPlacedEvent.of(
                auction.getId(),
                auction.getCurrentPrice(),
                auction.getScheduledEndTime(),
                extended,
                auction.getNextMinBidPrice(),
                auction.getBidIncrement(),
                auction.getTotalBidCount()
        );
        eventPublisher.publishEvent(event);
    }
}
