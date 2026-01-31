package com.cos.fairbid.trade.domain;

/**
 * 거래 상태
 */
public enum TradeStatus {

    /**
     * 거래 방식 선택 대기 (둘 다 가능한 경매의 경우)
     */
    AWAITING_METHOD_SELECTION,

    /**
     * 거래 조율 중
     * - 직거래: 시간 조율 중
     * - 택배: 주소 입력 대기 또는 발송 대기
     */
    AWAITING_ARRANGEMENT,

    /**
     * 거래 조율 완료
     * - 직거래: 약속 확정
     * - 택배: 발송 완료
     */
    ARRANGED,

    /**
     * 거래 완료
     */
    COMPLETED,

    /**
     * 거래 취소 (노쇼/유찰)
     */
    CANCELLED
}
