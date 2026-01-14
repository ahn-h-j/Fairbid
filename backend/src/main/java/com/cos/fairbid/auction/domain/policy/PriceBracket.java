package com.cos.fairbid.auction.domain.policy;

import java.util.Arrays;

/**
 * 가격 구간별 입찰 단위 정의
 *
 * | 현재 가격 구간        | 입찰 단위  |
 * |--------------------|----------|
 * | 1만 원 미만          | +500원   |
 * | 1만 ~ 5만 원 미만     | +1,000원 |
 * | 5만 ~ 10만 원 미만    | +3,000원 |
 * | 10만 ~ 50만 원 미만   | +5,000원 |
 * | 50만 ~ 100만 원 미만  | +10,000원|
 * | 100만 원 이상        | +30,000원|
 */
public enum PriceBracket {

    BELOW_10K(10_000L, 500L),
    BELOW_50K(50_000L, 1_000L),
    BELOW_100K(100_000L, 3_000L),
    BELOW_500K(500_000L, 5_000L),
    BELOW_1M(1_000_000L, 10_000L),
    FROM_1M(Long.MAX_VALUE, 30_000L);

    private final Long upperBound;
    private final Long increment;

    PriceBracket(Long upperBound, Long increment) {
        this.upperBound = upperBound;
        this.increment = increment;
    }

    public Long getUpperBound() {
        return upperBound;
    }

    public Long getIncrement() {
        return increment;
    }

    /**
     * 주어진 가격에 해당하는 입찰 단위를 반환한다
     *
     * @param price 현재 가격
     * @return 해당 가격 구간의 입찰 단위
     */
    public static Long getIncrementForPrice(Long price) {
        return Arrays.stream(values())
                .filter(bracket -> price < bracket.upperBound)
                .findFirst()
                .map(PriceBracket::getIncrement)
                .orElse(FROM_1M.increment);
    }
}
