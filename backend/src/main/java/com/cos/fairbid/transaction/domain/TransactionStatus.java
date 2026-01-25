package com.cos.fairbid.transaction.domain;

/**
 * 거래 상태를 나타내는 열거형
 */
public enum TransactionStatus {

    /** 결제 대기 중 */
    AWAITING_PAYMENT,

    /** 결제 완료 */
    PAID,

    /** 취소됨 (노쇼, 유찰 등) */
    CANCELLED
}
