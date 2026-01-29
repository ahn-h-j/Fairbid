package com.cos.fairbid.admin.adapter.in.dto;

import java.util.List;

/**
 * 시간 패턴 통계 응답 DTO
 *
 * @param hourlyBidCounts 시간대별 입찰 수 (0~23시)
 * @param peakHour        피크 시간대 (0~23)
 * @param peakCount       피크 시간대 입찰 수
 */
public record TimePatternResponse(
        List<HourlyBidCount> hourlyBidCounts,
        int peakHour,
        long peakCount
) {
    /**
     * 시간대별 입찰 수
     *
     * @param hour  시간 (0~23)
     * @param count 입찰 수
     */
    public record HourlyBidCount(int hour, long count) {
    }
}
