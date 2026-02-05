package com.cos.fairbid.trade.domain;

/**
 * 택배 배송 상태
 *
 * 흐름: AWAITING_ADDRESS → AWAITING_PAYMENT → SHIPPED → DELIVERED
 */
public enum DeliveryStatus {

    /**
     * 배송지 입력 대기
     */
    AWAITING_ADDRESS,

    /**
     * 입금 대기
     * 배송지 입력 완료 후 구매자가 입금해야 하는 상태
     */
    AWAITING_PAYMENT,

    /**
     * 발송 완료 (송장 입력됨)
     * 구매자 입금 확인 후 판매자가 상품 발송
     */
    SHIPPED,

    /**
     * 배송 완료 (수령 확인됨)
     */
    DELIVERED
}
