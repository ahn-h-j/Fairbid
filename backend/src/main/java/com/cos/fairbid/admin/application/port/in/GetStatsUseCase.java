package com.cos.fairbid.admin.application.port.in;

import com.cos.fairbid.admin.adapter.in.dto.DailyAuctionStatsResponse;
import com.cos.fairbid.admin.adapter.in.dto.StatsOverviewResponse;
import com.cos.fairbid.admin.adapter.in.dto.TimePatternResponse;

/**
 * 통계 조회 UseCase
 * 관리자 대시보드용 통계 데이터를 조회한다.
 */
public interface GetStatsUseCase {

    /**
     * 통계 개요를 조회한다.
     *
     * @param days 조회 기간 (일) - null이면 전체
     * @return 통계 개요
     */
    StatsOverviewResponse getOverview(Integer days);

    /**
     * 일별 경매 통계를 조회한다.
     *
     * @param days 조회 기간 (일) - null이면 전체
     * @return 일별 통계
     */
    DailyAuctionStatsResponse getDailyStats(Integer days);

    /**
     * 시간대별 입찰 패턴을 조회한다.
     *
     * @param days 조회 기간 (일) - null이면 전체
     * @return 시간 패턴 통계
     */
    TimePatternResponse getTimePattern(Integer days);
}
