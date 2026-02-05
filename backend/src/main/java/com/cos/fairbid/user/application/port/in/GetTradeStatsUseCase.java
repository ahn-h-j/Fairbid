package com.cos.fairbid.user.application.port.in;

/**
 * 사용자 거래 통계 조회 UseCase
 */
public interface GetTradeStatsUseCase {

    /**
     * 사용자의 거래 통계를 조회한다
     *
     * @param userId 사용자 ID
     * @return 거래 통계
     */
    TradeStats getTradeStats(Long userId);

    /**
     * 거래 통계 결과
     *
     * @param completedSales      완료된 판매 수
     * @param completedPurchases  완료된 구매 수
     * @param totalSalesAmount    총 판매 금액
     * @param totalPurchaseAmount 총 구매 금액
     */
    record TradeStats(
            int completedSales,
            int completedPurchases,
            long totalSalesAmount,
            long totalPurchaseAmount
    ) {
    }
}
