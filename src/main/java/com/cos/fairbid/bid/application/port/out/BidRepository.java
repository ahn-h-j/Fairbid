package com.cos.fairbid.bid.application.port.out;

import com.cos.fairbid.bid.domain.Bid;

import java.util.List;

/**
 * 입찰 저장소 아웃바운드 포트
 * 영속성 계층과의 통신을 위한 인터페이스
 */
public interface BidRepository {

    /**
     * 입찰 이력을 저장한다
     *
     * @param bid 저장할 입찰 도메인 객체
     * @return 저장된 입찰 (ID 포함)
     */
    Bid save(Bid bid);

    /**
     * 경매의 상위 2개 입찰을 조회한다 (금액 내림차순)
     * 낙찰자 결정 시 1, 2순위 추출에 사용
     *
     * @param auctionId 경매 ID
     * @return 상위 2개 입찰 목록 (금액 내림차순)
     */
    List<Bid> findTop2ByAuctionId(Long auctionId);
}
