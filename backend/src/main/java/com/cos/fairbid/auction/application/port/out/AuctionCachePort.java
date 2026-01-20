package com.cos.fairbid.auction.application.port.out;

import com.cos.fairbid.auction.domain.Auction;

/**
 * 경매 캐시 아웃바운드 포트
 * Redis 캐시 인터페이스
 */
public interface AuctionCachePort {

    /**
     * 경매 정보를 캐시에 저장한다
     *
     * @param auction 경매 도메인 객체
     */
    void saveToCache(Auction auction);
}
