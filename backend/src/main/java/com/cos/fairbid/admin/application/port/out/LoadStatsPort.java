package com.cos.fairbid.admin.application.port.out;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 통계 데이터 로드 Port
 * 통계 집계를 위한 데이터를 조회한다.
 */
public interface LoadStatsPort {

    /**
     * 기간 내 전체 경매 수를 조회한다.
     */
    long countTotalAuctions(LocalDateTime from);

    /**
     * 기간 내 낙찰 완료 경매 수를 조회한다.
     */
    long countCompletedAuctions(LocalDateTime from);

    /**
     * 기간 내 평균 입찰 수를 조회한다.
     */
    double getAvgBidCount(LocalDateTime from);

    /**
     * 기간 내 낙찰된 경매의 평균 가격 상승률을 조회한다.
     */
    double getAvgPriceIncreaseRate(LocalDateTime from);

    /**
     * 기간 내 연장이 발생한 경매 수를 조회한다.
     */
    long countExtendedAuctions(LocalDateTime from);

    /**
     * 시간대별 입찰 수를 조회한다.
     *
     * @return 시간대(0~23)별 입찰 수 리스트
     */
    List<HourlyBidCount> getHourlyBidCounts(LocalDateTime from);

    /**
     * 일별 신규 경매 수를 조회한다.
     */
    List<DailyCount> getDailyNewAuctions(LocalDateTime from);

    /**
     * 일별 낙찰 완료 경매 수를 조회한다.
     */
    List<DailyCount> getDailyCompletedAuctions(LocalDateTime from);

    /**
     * 일별 입찰 수를 조회한다.
     */
    List<DailyCount> getDailyBids(LocalDateTime from);

    /**
     * 시간대별 입찰 수
     */
    record HourlyBidCount(int hour, long count) {
    }

    /**
     * 일별 카운트
     */
    record DailyCount(java.time.LocalDate date, long count) {
    }
}
