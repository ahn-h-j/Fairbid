package com.cos.fairbid.auction.domain.policy;

import java.time.LocalDateTime;

/**
 * 경매 연장 정책
 * 종료 직전 입찰 시 경매 시간 연장 규칙을 정의한다
 */
public class AuctionExtensionPolicy {

    /**
     * 연장 구간 (종료 N분 전부터 연장 가능)
     */
    private static final int EXTENSION_THRESHOLD_MINUTES = 5;

    /**
     * 연장 시간 (분)
     */
    private static final int EXTENSION_DURATION_MINUTES = 5;

    private AuctionExtensionPolicy() {
        // 유틸리티 클래스
    }

    /**
     * 현재 시점이 연장 구간인지 확인한다
     * 종료 5분 전부터 종료 시점까지가 연장 구간
     *
     * @param scheduledEndTime 예정 종료 시간
     * @param now              현재 시간
     * @return 연장 구간이면 true
     */
    public static boolean isInExtensionPeriod(LocalDateTime scheduledEndTime, LocalDateTime now) {
        LocalDateTime extensionThreshold = scheduledEndTime.minusMinutes(EXTENSION_THRESHOLD_MINUTES);
        return now.isAfter(extensionThreshold) && now.isBefore(scheduledEndTime);
    }

    /**
     * 연장된 종료 시간을 계산한다
     * 현재 시점 기준 5분 후로 설정
     *
     * @param now 현재 시간
     * @return 연장된 종료 시간
     */
    public static LocalDateTime calculateExtendedEndTime(LocalDateTime now) {
        return now.plusMinutes(EXTENSION_DURATION_MINUTES);
    }

    /**
     * 연장 구간 임계값 (분)을 반환한다
     *
     * @return 연장 구간 임계값
     */
    public static int getExtensionThresholdMinutes() {
        return EXTENSION_THRESHOLD_MINUTES;
    }

    /**
     * 연장 시간 (분)을 반환한다
     *
     * @return 연장 시간
     */
    public static int getExtensionDurationMinutes() {
        return EXTENSION_DURATION_MINUTES;
    }
}
