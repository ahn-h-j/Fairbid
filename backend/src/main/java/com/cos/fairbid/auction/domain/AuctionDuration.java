package com.cos.fairbid.auction.domain;

import java.time.Duration;

/**
 * 경매 기간을 나타내는 열거형
 * 판매자가 24시간 또는 48시간 중 선택
 */
public enum AuctionDuration {

    HOURS_24(Duration.ofHours(24)),
    HOURS_48(Duration.ofHours(48));

    private final Duration duration;

    AuctionDuration(Duration duration) {
        this.duration = duration;
    }

    public Duration getDuration() {
        return duration;
    }
}
