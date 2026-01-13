package com.cos.fairbid.auction.application.port.in;

import com.cos.fairbid.auction.domain.Auction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * 경매 목록 조회 유스케이스 인터페이스
 */
public interface GetAuctionListUseCase {

    /**
     * 경매 목록을 조회한다
     *
     * @param condition 검색 조건 (status, keyword)
     * @param pageable  페이지네이션 정보
     * @return 경매 목록 (페이지)
     */
    Page<Auction> getAuctionList(AuctionSearchCondition condition, Pageable pageable);
}
