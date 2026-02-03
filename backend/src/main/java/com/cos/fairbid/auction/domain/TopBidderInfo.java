package com.cos.fairbid.auction.domain;

/**
 * Redis에서 조회한 최고 입찰자 정보
 * 낙찰자 결정 시 사용되는 도메인 객체
 *
 * @param bidderId  입찰자 ID
 * @param bidAmount 입찰 금액
 */
public record TopBidderInfo(
        Long bidderId,
        Long bidAmount
) {
    /**
     * 유효한 입찰 정보인지 확인
     *
     * @return bidderId와 bidAmount가 모두 존재하면 true
     */
    public boolean isValid() {
        return bidderId != null && bidAmount != null && bidAmount > 0;
    }
}
