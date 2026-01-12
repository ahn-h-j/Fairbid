package com.cos.fairbid.bid.domain;

/**
 * 입찰 유형
 */
public enum BidType {

    /**
     * 원터치 입찰 - 현재가 + 입찰단위로 자동 입찰
     */
    ONE_TOUCH,

    /**
     * 금액 직접 지정 - 사용자가 입찰 금액을 직접 입력
     */
    DIRECT
}
