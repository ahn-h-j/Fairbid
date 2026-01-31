package com.cos.fairbid.trade.domain;

/**
 * 택배 배송 상태
 */
public enum DeliveryStatus {

    /**
     * 배송지 입력 대기
     */
    AWAITING_ADDRESS,

    /**
     * 배송지 입력 완료 (발송 대기)
     */
    ADDRESS_SUBMITTED,

    /**
     * 발송 완료 (송장 입력됨)
     */
    SHIPPED,

    /**
     * 배송 완료 (수령 확인됨)
     */
    DELIVERED
}
