package com.cos.fairbid.bid.application.port.out;

import com.cos.fairbid.auction.domain.Auction;

/**
 * 입찰 이벤트 발행 아웃바운드 포트
 * 실시간 UI 업데이트를 위한 이벤트 발행 인터페이스
 */
public interface BidEventPublisher {

    /**
     * 입찰 완료 이벤트를 발행한다
     * 실시간 UI 업데이트용 (현재가, 종료시간, 다음 입찰가, 입찰 단위, 총 입찰수)
     *
     * @param auction  경매 도메인 객체
     * @param extended 연장 여부
     */
    void publishBidPlaced(Auction auction, boolean extended);
}
