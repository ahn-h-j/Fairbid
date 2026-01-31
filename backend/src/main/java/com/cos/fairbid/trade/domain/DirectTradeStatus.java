package com.cos.fairbid.trade.domain;

/**
 * 직거래 조율 상태
 */
public enum DirectTradeStatus {

    /**
     * 시간 제안됨
     */
    PROPOSED,

    /**
     * 역제안됨
     */
    COUNTER_PROPOSED,

    /**
     * 수락됨 (약속 확정)
     */
    ACCEPTED
}
