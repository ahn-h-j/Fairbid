package com.cos.fairbid.winning.application.port.in;

/**
 * 경매 종료 유스케이스 인터페이스
 */
public interface CloseAuctionUseCase {

    /**
     * 종료 시간이 도래한 경매들을 일괄 종료 처리한다
     * 스케줄러에서 주기적으로 호출
     */
    void closeExpiredAuctions();
}
