package com.cos.fairbid.auction.domain.policy;

/**
 * 입찰 단위 정책
 * 가격 구간별 기본 입찰 단위와 연장 할증을 계산한다
 */
public class BidIncrementPolicy {

    /**
     * 연장 N회마다 할증 적용
     */
    private static final int EXTENSION_SURCHARGE_INTERVAL = 3;

    /**
     * 할증 비율 (50%)
     */
    private static final double SURCHARGE_RATE = 0.5;

    private BidIncrementPolicy() {
        // 유틸리티 클래스
    }

    /**
     * 가격 구간에 따른 기본 입찰 단위를 계산한다
     *
     * @param currentPrice 현재 가격
     * @return 기본 입찰 단위
     */
    public static Long calculateBaseIncrement(Long currentPrice) {
        return PriceBracket.getIncrementForPrice(currentPrice);
    }

    /**
     * 연장 횟수에 따른 할증된 입찰 단위를 계산한다
     * 연장 3회마다 기본 입찰 단위에 50%씩 추가
     *
     * @param baseIncrement  기본 입찰 단위
     * @param extensionCount 연장 횟수
     * @return 할증된 입찰 단위
     */
    public static Long calculateAdjustedIncrement(Long baseIncrement, int extensionCount) {
        int surchargeMultiplier = extensionCount / EXTENSION_SURCHARGE_INTERVAL;
        double surchargeRate = 1.0 + (surchargeMultiplier * SURCHARGE_RATE);
        return Math.round(baseIncrement * surchargeRate);
    }

    /**
     * 현재 가격과 연장 횟수를 기반으로 최종 입찰 단위를 계산한다
     *
     * @param currentPrice   현재 가격
     * @param extensionCount 연장 횟수
     * @return 최종 입찰 단위 (기본 + 할증)
     */
    public static Long calculateFinalIncrement(Long currentPrice, int extensionCount) {
        Long baseIncrement = calculateBaseIncrement(currentPrice);
        return calculateAdjustedIncrement(baseIncrement, extensionCount);
    }
}
