package com.cos.fairbid.winning.domain;

/**
 * 낙찰 상태를 나타내는 열거형
 */
public enum WinningStatus {

    /** 결제 대기 중 */
    PENDING_PAYMENT,

    /** 결제 완료 */
    PAID,

    /** 미결제 (노쇼) */
    NO_SHOW,

    /** 유찰 (2순위 < 90%인 경우) */
    FAILED
}
