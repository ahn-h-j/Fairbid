package com.cos.fairbid.winning.adapter.in.scheduler;

import com.cos.fairbid.winning.application.port.in.CloseAuctionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 경매 종료 스케줄러
 * 매초 실행되어 종료 시간이 도래한 경매를 처리한다
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionClosingScheduler {

    private final CloseAuctionUseCase closeAuctionUseCase;

    /**
     * 매초 실행되어 종료 대상 경매를 처리한다
     * fixedRate = 1000ms (1초)
     */
    @Scheduled(fixedRate = 1000)
    public void pollClosingAuctions() {
        try {
            closeAuctionUseCase.closeExpiredAuctions();
        } catch (Exception e) {
            log.error("경매 종료 스케줄러 실행 중 오류 발생", e);
        }
    }
}
