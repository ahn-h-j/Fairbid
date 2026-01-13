package com.cos.fairbid.winning.application.port.out;

/**
 * 경매 종료 이벤트 발행 아웃바운드 포트
 * 트랜잭션 커밋 후 브로드캐스트를 위한 이벤트 발행 인터페이스
 */
public interface AuctionClosedEventPublisher {

    /**
     * 경매 종료 이벤트를 발행한다
     * 트랜잭션 커밋 후 AuctionClosedEventListener에서 브로드캐스트 실행
     *
     * @param auctionId 종료된 경매 ID
     */
    void publishAuctionClosed(Long auctionId);
}
