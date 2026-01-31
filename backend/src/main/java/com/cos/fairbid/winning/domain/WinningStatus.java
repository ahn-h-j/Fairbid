package com.cos.fairbid.winning.domain;

/**
 * 낙찰 상태를 나타내는 열거형
 * 결제 → 응답 기반 시스템으로 변경됨
 */
public enum WinningStatus {

    /** 2순위 대기 중 (1순위가 응답 중) */
    STANDBY,

    /** 응답 대기 중 (거래 방식 조율 대기) */
    PENDING_RESPONSE,

    /** 응답 완료 (거래 조율 시작됨) */
    RESPONDED,

    /** 미응답 (노쇼) */
    NO_SHOW,

    /** 유찰 (2순위 < 90%인 경우) */
    FAILED
}
