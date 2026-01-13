package com.cos.fairbid.auction.domain;

/**
 * 경매 상태를 나타내는 열거형
 */
public enum AuctionStatus {

    /** 입찰 진행 중 */
    BIDDING,

    /** 즉시 구매 발생 후 최종 입찰 기간 (1시간) */
    INSTANT_BUY_PENDING,

    /** 경매 종료 (낙찰자 결정) */
    ENDED,

    /** 유찰 (입찰자 없음 또는 2순위 승계 실패) */
    FAILED,

    /** 경매 취소됨 */
    CANCELLED
}
