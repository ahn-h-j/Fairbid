package com.cos.fairbid.admin.application.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 일별 경매 통계 결과 DTO (Application Layer)
 *
 * @param dailyStats 일별 통계 목록
 */
public record DailyAuctionStatsResult(
        List<DailyStat> dailyStats
) {
    /**
     * 일별 통계
     *
     * @param date              날짜
     * @param newAuctions       신규 등록 경매 수
     * @param completedAuctions 낙찰 완료 경매 수
     * @param bids              입찰 수
     */
    public record DailyStat(
            LocalDate date,
            long newAuctions,
            long completedAuctions,
            long bids
    ) {
    }
}
