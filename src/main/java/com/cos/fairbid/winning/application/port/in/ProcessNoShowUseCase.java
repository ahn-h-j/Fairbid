package com.cos.fairbid.winning.application.port.in;

/**
 * 노쇼 처리 유스케이스 인터페이스
 */
public interface ProcessNoShowUseCase {

    /**
     * 결제 기한이 만료된 낙찰 건들을 일괄 처리한다
     * - 1순위 노쇼 시: 경고 부여 + 2순위 승계 또는 유찰
     * - 2순위 노쇼 시: 유찰 (노쇼 처리 안함)
     */
    void processExpiredPayments();

    /**
     * 단일 낙찰 건의 노쇼를 처리한다
     *
     * @param winningId 낙찰 ID
     */
    void processNoShow(Long winningId);
}
