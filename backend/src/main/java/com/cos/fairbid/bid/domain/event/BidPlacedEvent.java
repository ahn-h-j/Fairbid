package com.cos.fairbid.bid.domain.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 입찰 완료 이벤트
 * 입찰이 성공적으로 처리된 후 발행되며, 실시간 UI 업데이트에 활용
 * (현재가, 종료시간 등 실시간 반영)
 */
@Getter
public class BidPlacedEvent {

    private final Long auctionId;
    private final Long currentPrice;
    private final LocalDateTime scheduledEndTime;
    private final boolean extended;
    private final LocalDateTime occurredAt;

    private BidPlacedEvent(
            Long auctionId,
            Long currentPrice,
            LocalDateTime scheduledEndTime,
            boolean extended
    ) {
        this.auctionId = auctionId;
        this.currentPrice = currentPrice;
        this.scheduledEndTime = scheduledEndTime;
        this.extended = extended;
        this.occurredAt = LocalDateTime.now();
    }

    /**
     * 입찰 완료 이벤트 생성
     *
     * @param auctionId        경매 ID
     * @param currentPrice     현재가 (입찰 후)
     * @param scheduledEndTime 종료 예정 시간 (연장 시 갱신됨)
     * @param extended         경매 연장 여부
     * @return BidPlacedEvent 인스턴스
     */
    public static BidPlacedEvent of(
            Long auctionId,
            Long currentPrice,
            LocalDateTime scheduledEndTime,
            boolean extended
    ) {
        return new BidPlacedEvent(
                auctionId,
                currentPrice,
                scheduledEndTime,
                extended
        );
    }
}
