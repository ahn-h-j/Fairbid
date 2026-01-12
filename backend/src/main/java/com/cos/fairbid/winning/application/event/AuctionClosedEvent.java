package com.cos.fairbid.winning.application.event;

/**
 * 경매 종료 이벤트
 * 트랜잭션 커밋 후 브로드캐스트를 위해 사용
 *
 * @param auctionId 종료된 경매 ID
 */
public record AuctionClosedEvent(Long auctionId) {
}
