package com.cos.fairbid.auction.application.port.in;

import com.cos.fairbid.auction.domain.Auction;

/**
 * 경매 상세 조회 유스케이스 인터페이스
 */
public interface GetAuctionDetailUseCase {

    /**
     * 경매 상세 정보를 조회한다
     *
     * @param auctionId 조회할 경매 ID
     * @return 경매 도메인 객체
     * @throws com.cos.fairbid.auction.domain.exception.AuctionNotFoundException 경매가 존재하지 않을 경우
     */
    Auction getAuctionDetail(Long auctionId);
}
