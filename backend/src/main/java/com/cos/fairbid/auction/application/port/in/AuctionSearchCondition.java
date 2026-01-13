package com.cos.fairbid.auction.application.port.in;

import com.cos.fairbid.auction.domain.AuctionStatus;

/**
 * 경매 목록 검색 조건
 *
 * @param status  경매 상태 필터 (선택)
 * @param keyword 검색어 - 상품명 (선택)
 */
public record AuctionSearchCondition(
        AuctionStatus status,
        String keyword
) {
    /**
     * 빈 검색 조건 생성
     */
    public static AuctionSearchCondition empty() {
        return new AuctionSearchCondition(null, null);
    }

    /**
     * keyword가 유효한지 확인
     */
    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }

    /**
     * status가 유효한지 확인
     */
    public boolean hasStatus() {
        return status != null;
    }
}
