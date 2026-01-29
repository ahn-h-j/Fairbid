package com.cos.fairbid.admin.adapter.in.dto;

/**
 * 통계 개요 응답 DTO
 *
 * @param totalAuctions       전체 경매 수
 * @param completedRate       낙찰률 (%)
 * @param avgBidCount         평균 경쟁률 (입찰자 수)
 * @param avgPriceIncreaseRate 평균 상승률 (%)
 * @param extensionRate       연장 발생률 (%)
 */
public record StatsOverviewResponse(
        long totalAuctions,
        double completedRate,
        double avgBidCount,
        double avgPriceIncreaseRate,
        double extensionRate
) {
}
