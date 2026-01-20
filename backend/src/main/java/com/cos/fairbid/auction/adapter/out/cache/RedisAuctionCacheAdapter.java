package com.cos.fairbid.auction.adapter.out.cache;

import com.cos.fairbid.auction.application.port.out.AuctionCachePort;
import com.cos.fairbid.auction.domain.Auction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

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

    /**
     * Auction 도메인을 Redis Hash Map으로 변환
     */
    private Map<String, String> auctionToMap(Auction auction) {
        // scheduledEndTime을 밀리초로 변환 (Lua 스크립트에서 연장 판단용)
        long scheduledEndTimeMs = auction.getScheduledEndTime()
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        return Map.ofEntries(
                Map.entry("sellerId", String.valueOf(auction.getSellerId())),
                Map.entry("title", auction.getTitle()),
                Map.entry("description", auction.getDescription() != null ? auction.getDescription() : ""),
                Map.entry("category", auction.getCategory().name()),
                Map.entry("startPrice", String.valueOf(auction.getStartPrice())),
                Map.entry("currentPrice", String.valueOf(auction.getCurrentPrice())),
                Map.entry("instantBuyPrice", auction.getInstantBuyPrice() != null ? String.valueOf(auction.getInstantBuyPrice()) : ""),
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
                Map.entry("updatedAt", auction.getUpdatedAt() != null ? auction.getUpdatedAt().toString() : "")
        );
    }
}
