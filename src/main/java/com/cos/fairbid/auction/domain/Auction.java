package com.cos.fairbid.auction.domain;

import com.cos.fairbid.auction.domain.exception.InvalidAuctionException;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 경매 도메인 모델
 * 순수 비즈니스 로직만 포함 (JPA 의존성 없음)
 */
@Getter
@Builder
public class Auction {

    private Long id;
    private Long sellerId;
    private String title;
    private String description;
    private Category category;
    private Long startPrice;
    private Long currentPrice;
    private Long instantBuyPrice;
    private Long bidIncrement;
    private LocalDateTime scheduledEndTime;
    private LocalDateTime actualEndTime;
    private Integer extensionCount;
    private Integer totalBidCount;
    private AuctionStatus status;
    private Long winnerId;
    private List<String> imageUrls;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 새로운 경매 생성을 위한 정적 팩토리 메서드
     *
     * @param sellerId        판매자 ID
     * @param title           경매 제목
     * @param description     경매 설명
     * @param category        카테고리
     * @param startPrice      시작가
     * @param instantBuyPrice 즉시구매가 (nullable)
     * @param duration        경매 기간 (24h/48h)
     * @param imageUrls       이미지 URL 목록
     * @return 생성된 Auction 도메인 객체
     */
    public static Auction create(
            Long sellerId,
            String title,
            String description,
            Category category,
            Long startPrice,
            Long instantBuyPrice,
            AuctionDuration duration,
            List<String> imageUrls
    ) {
        // 즉시구매가 검증: 시작가보다 높아야 함
        if (instantBuyPrice != null && instantBuyPrice <= startPrice) {
            throw InvalidAuctionException.instantBuyPriceTooLow(startPrice, instantBuyPrice);
        }

        LocalDateTime now = LocalDateTime.now();

        return Auction.builder()
                .sellerId(sellerId)
                .title(title)
                .description(description)
                .category(category)
                .startPrice(startPrice)
                .currentPrice(startPrice)
                .instantBuyPrice(instantBuyPrice)
                .bidIncrement(calculateBidIncrement(startPrice))
                .scheduledEndTime(now.plus(duration.getDuration()))
                .actualEndTime(null)
                .extensionCount(0)
                .totalBidCount(0)
                .status(AuctionStatus.BIDDING)
                .winnerId(null)
                .imageUrls(imageUrls)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * 현재 가격 구간에 따른 입찰 단위 계산
     *
     * | 현재 가격 구간        | 입찰 단위  |
     * |--------------------|----------|
     * | 1만 원 미만          | +500원   |
     * | 1만 ~ 5만 원 미만     | +1,000원 |
     * | 5만 ~ 10만 원 미만    | +3,000원 |
     * | 10만 ~ 50만 원 미만   | +5,000원 |
     * | 50만 ~ 100만 원 미만  | +10,000원|
     * | 100만 원 이상        | +30,000원|
     *
     * @param price 현재 가격
     * @return 입찰 단위
     */
    public static Long calculateBidIncrement(Long price) {
        if (price < 10_000L) {
            return 500L;
        } else if (price < 50_000L) {
            return 1_000L;
        } else if (price < 100_000L) {
            return 3_000L;
        } else if (price < 500_000L) {
            return 5_000L;
        } else if (price < 1_000_000L) {
            return 10_000L;
        } else {
            return 30_000L;
        }
    }

    /**
     * 영속성 계층에서 조회한 데이터로 도메인 객체 복원
     * Mapper에서 사용
     */
    public static AuctionBuilder reconstitute() {
        return Auction.builder();
    }

    // =====================================================
    // 비즈니스 로직 메서드
    // =====================================================

    /**
     * 즉시 구매 버튼 활성화 여부를 반환한다
     *
     * 활성화 조건:
     * 1. 즉시구매가가 설정되어 있어야 함
     * 2. 현재가가 즉시구매가의 90% 미만이어야 함
     *
     * @return 즉시 구매 가능하면 true, 아니면 false
     */
    public boolean isInstantBuyEnabled() {
        // 즉시구매가가 설정되지 않았으면 비활성화
        if (instantBuyPrice == null) {
            return false;
        }

        // 현재가가 즉시구매가의 90% 이상이면 비활성화
        long threshold = (long) (instantBuyPrice * 0.9);
        return currentPrice < threshold;
    }

    /**
     * 다음 입찰 가능 최소 금액을 반환한다
     * 현재 최고가 + 입찰 단위
     *
     * @return 다음 입찰 가능 최소 금액
     */
    public Long getNextMinBidPrice() {
        return currentPrice + bidIncrement;
    }

    /**
     * 경매 수정 가능 여부를 반환한다
     * 첫 입찰이 발생하기 전에만 수정 가능
     *
     * @return 수정 가능하면 true, 아니면 false
     */
    public boolean isEditable() {
        return totalBidCount == 0;
    }
}
