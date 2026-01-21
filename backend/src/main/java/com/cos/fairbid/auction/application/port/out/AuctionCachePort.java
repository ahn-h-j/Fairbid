package com.cos.fairbid.auction.application.port.out;

import com.cos.fairbid.auction.domain.Auction;

import java.util.Optional;

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

    /**
     * 캐시에서 경매 정보를 조회한다
     *
     * @param auctionId 경매 ID
     * @return 경매 도메인 객체 (캐시 미스 시 빈 Optional)
     */
    Optional<Auction> findById(Long auctionId);

    /**
     * 캐시에 경매 정보가 존재하는지 확인한다
     *
     * @param auctionId 경매 ID
     * @return 존재 여부
     */
    boolean existsInCache(Long auctionId);
}
