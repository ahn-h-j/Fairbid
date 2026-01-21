package com.cos.fairbid.auction.adapter.out.cache;

import com.cos.fairbid.auction.application.port.out.AuctionCachePort;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.auction.domain.Category;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Redis 기반 경매 캐시 어댑터
 * 입찰 처리를 위한 경매 정보 캐싱
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisAuctionCacheAdapter implements AuctionCachePort {

    private static final String AUCTION_KEY_PREFIX = "auction:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void saveToCache(Auction auction) {
        String key = AUCTION_KEY_PREFIX + auction.getId();
        Map<String, String> data = auctionToMap(auction);

        redisTemplate.opsForHash().putAll(key, data);
        log.debug("경매 캐시 저장: auctionId={}", auction.getId());
    }

    @Override
    public Optional<Auction> findById(Long auctionId) {
        String key = AUCTION_KEY_PREFIX + auctionId;
        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);

        if (data.isEmpty()) {
            log.debug("경매 캐시 미스: auctionId={}", auctionId);
            return Optional.empty();
        }

        log.debug("경매 캐시 히트: auctionId={}", auctionId);
        return Optional.of(mapToAuction(auctionId, data));
    }

    @Override
    public boolean existsInCache(Long auctionId) {
        String key = AUCTION_KEY_PREFIX + auctionId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Auction 도메인을 Redis Hash Map으로 변환
     */
    private Map<String, String> auctionToMap(Auction auction) {
        // scheduledEndTime을 밀리초로 변환 (Lua 스크립트에서 연장 판단용)
        long scheduledEndTimeMs = auction.getScheduledEndTime()
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        // instantBuyActivatedTime을 밀리초로 변환 (Lua 스크립트에서 사용)
        String instantBuyActivatedTimeMs = "";
        if (auction.getInstantBuyActivatedTime() != null) {
            instantBuyActivatedTimeMs = String.valueOf(
                    auction.getInstantBuyActivatedTime()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
            );
        }

        return Map.ofEntries(
                Map.entry("sellerId", String.valueOf(auction.getSellerId())),
                Map.entry("title", auction.getTitle()),
                Map.entry("description", auction.getDescription() != null ? auction.getDescription() : ""),
                Map.entry("category", auction.getCategory().name()),
                Map.entry("startPrice", String.valueOf(auction.getStartPrice())),
                Map.entry("currentPrice", String.valueOf(auction.getCurrentPrice())),
                Map.entry("instantBuyPrice", auction.getInstantBuyPrice() != null ? String.valueOf(auction.getInstantBuyPrice()) : "0"),
                Map.entry("bidIncrement", String.valueOf(auction.getBidIncrement())),
                Map.entry("scheduledEndTime", auction.getScheduledEndTime().toString()),
                Map.entry("scheduledEndTimeMs", String.valueOf(scheduledEndTimeMs)),
                Map.entry("actualEndTime", auction.getActualEndTime() != null ? auction.getActualEndTime().toString() : ""),
                Map.entry("extensionCount", String.valueOf(auction.getExtensionCount())),
                Map.entry("totalBidCount", String.valueOf(auction.getTotalBidCount())),
                Map.entry("status", auction.getStatus().name()),
                Map.entry("winnerId", auction.getWinnerId() != null ? String.valueOf(auction.getWinnerId()) : ""),
                Map.entry("imageUrls", auction.getImageUrls() != null ? String.join(",", auction.getImageUrls()) : ""),
                Map.entry("createdAt", auction.getCreatedAt() != null ? auction.getCreatedAt().toString() : ""),
                Map.entry("updatedAt", auction.getUpdatedAt() != null ? auction.getUpdatedAt().toString() : ""),
                // 즉시 구매 관련 필드
                Map.entry("instantBuyerId", auction.getInstantBuyerId() != null ? String.valueOf(auction.getInstantBuyerId()) : ""),
                Map.entry("instantBuyActivatedTimeMs", instantBuyActivatedTimeMs)
        );
    }

    /**
     * Redis Hash Map을 Auction 도메인으로 변환
     */
    private Auction mapToAuction(Long auctionId, Map<Object, Object> data) {
        // 필수 필드 파싱
        Long sellerId = parseLong(data.get("sellerId"));
        String title = parseString(data.get("title"));
        String description = parseString(data.get("description"));
        Category category = Category.valueOf(parseString(data.get("category")));
        Long startPrice = parseLong(data.get("startPrice"));
        Long currentPrice = parseLong(data.get("currentPrice"));
        Long instantBuyPrice = parseLongOrNull(data.get("instantBuyPrice"));
        Long bidIncrement = parseLong(data.get("bidIncrement"));
        Integer extensionCount = parseInt(data.get("extensionCount"));
        Integer totalBidCount = parseInt(data.get("totalBidCount"));
        AuctionStatus status = AuctionStatus.valueOf(parseString(data.get("status")));

        // 시간 필드 파싱 (밀리초 기준)
        LocalDateTime scheduledEndTime = parseLocalDateTime(data.get("scheduledEndTimeMs"));
        LocalDateTime actualEndTime = parseLocalDateTimeOrNull(data.get("actualEndTime"));
        LocalDateTime createdAt = parseLocalDateTimeOrNull(data.get("createdAt"));
        LocalDateTime updatedAt = parseLocalDateTimeOrNull(data.get("updatedAt"));

        // 낙찰자 정보
        Long winnerId = parseLongOrNull(data.get("winnerId"));

        // 이미지 URL
        String imageUrlsStr = parseString(data.get("imageUrls"));
        List<String> imageUrls = imageUrlsStr.isEmpty() ? List.of() : Arrays.asList(imageUrlsStr.split(","));

        // 즉시 구매 관련 필드
        Long instantBuyerId = parseLongOrNull(data.get("instantBuyerId"));
        LocalDateTime instantBuyActivatedTime = parseLocalDateTimeFromMs(data.get("instantBuyActivatedTimeMs"));

        // Auction 도메인 빌더를 통해 객체 생성
        return Auction.builder()
                .id(auctionId)
                .sellerId(sellerId)
                .title(title)
                .description(description)
                .category(category)
                .startPrice(startPrice)
                .currentPrice(currentPrice)
                .instantBuyPrice(instantBuyPrice)
                .bidIncrement(bidIncrement)
                .scheduledEndTime(scheduledEndTime)
                .actualEndTime(actualEndTime)
                .extensionCount(extensionCount)
                .totalBidCount(totalBidCount)
                .status(status)
                .winnerId(winnerId)
                .imageUrls(imageUrls)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .instantBuyerId(instantBuyerId)
                .instantBuyActivatedTime(instantBuyActivatedTime)
                .build();
    }

    // ============================
    // 파싱 유틸리티 메서드
    // ============================

    private String parseString(Object value) {
        return value != null ? value.toString() : "";
    }

    private Long parseLong(Object value) {
        if (value == null || value.toString().isEmpty()) {
            return 0L;
        }
        return Long.parseLong(value.toString());
    }

    private Long parseLongOrNull(Object value) {
        if (value == null || value.toString().isEmpty() || "0".equals(value.toString())) {
            return null;
        }
        return Long.parseLong(value.toString());
    }

    private Integer parseInt(Object value) {
        if (value == null || value.toString().isEmpty()) {
            return 0;
        }
        return Integer.parseInt(value.toString());
    }

    /**
     * 밀리초 값에서 LocalDateTime 파싱
     */
    private LocalDateTime parseLocalDateTime(Object msValue) {
        if (msValue == null || msValue.toString().isEmpty()) {
            return LocalDateTime.now();
        }
        long ms = Long.parseLong(msValue.toString());
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
    }

    /**
     * 밀리초 값에서 LocalDateTime 파싱 (nullable)
     */
    private LocalDateTime parseLocalDateTimeFromMs(Object msValue) {
        if (msValue == null || msValue.toString().isEmpty()) {
            return null;
        }
        long ms = Long.parseLong(msValue.toString());
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
    }

    /**
     * 문자열 LocalDateTime 파싱 (nullable) - 기존 형식 호환
     */
    private LocalDateTime parseLocalDateTimeOrNull(Object value) {
        if (value == null || value.toString().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
