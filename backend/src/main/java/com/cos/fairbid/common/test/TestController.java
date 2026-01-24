package com.cos.fairbid.common.test;

import com.cos.fairbid.auction.application.port.out.AuctionCachePort;
import com.cos.fairbid.auction.application.port.out.AuctionRepositoryPort;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.exception.AuctionNotFoundException;
import com.cos.fairbid.common.response.ApiResponse;
import com.cos.fairbid.winning.application.port.in.CloseAuctionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 테스트용 컨트롤러
 * 개발/테스트 환경에서만 사용 가능
 *
 * 주의: 프로덕션 환경에서는 비활성화되어야 함
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
public class TestController {

    private static final String AUCTION_KEY_PREFIX = "auction:";

    private final AuctionRepositoryPort auctionRepository;
    private final AuctionCachePort auctionCachePort;
    private final CloseAuctionUseCase closeAuctionUseCase;
    private final StringRedisTemplate redisTemplate;

    /**
     * 경매 종료 시간을 현재로부터 5분 후로 설정 (연장 테스트용)
     *
     * @param auctionId 경매 ID
     * @return 변경된 경매 정보
     */
    @PostMapping("/auctions/{auctionId}/set-ending-soon")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setEndingSoon(
            @PathVariable Long auctionId
    ) {
        // 1. DB에서 경매 조회
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> AuctionNotFoundException.withId(auctionId));

        // 2. 종료 시간을 현재 + 5분으로 변경 (Redis Hash + Sorted Set 업데이트)
        String key = AUCTION_KEY_PREFIX + auctionId;
        LocalDateTime newEndTime = LocalDateTime.now().plusMinutes(5);
        long newEndTimeMs = newEndTime.atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        redisTemplate.opsForHash().put(key, "scheduledEndTime", newEndTime.toString());
        redisTemplate.opsForHash().put(key, "scheduledEndTimeMs", String.valueOf(newEndTimeMs));
        auctionCachePort.addToClosingQueue(auctionId, newEndTimeMs);

        // 3. RDB도 함께 업데이트 (목록 조회 시 정확한 시간 표시를 위해)
        auction.updateScheduledEndTime(newEndTime);
        auctionRepository.save(auction);

        log.info("[TEST] 경매 종료 시간 변경: auctionId={}, newEndTime={}", auctionId, newEndTime);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "auctionId", auctionId,
                "newScheduledEndTime", newEndTime.toString(),
                "message", "경매 종료 시간이 5분 후로 변경되었습니다."
        )));
    }

    /**
     * 경매 종료 시간을 현재로부터 지정한 초 후로 설정
     *
     * @param auctionId 경매 ID
     * @param seconds   초 단위 시간
     * @return 변경된 경매 정보
     */
    @PostMapping("/auctions/{auctionId}/set-end-time")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setEndTime(
            @PathVariable Long auctionId,
            @RequestParam(defaultValue = "300") int seconds
    ) {
        // 1. DB에서 경매 조회
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> AuctionNotFoundException.withId(auctionId));

        // 2. 종료 시간 변경 (Redis Hash + Sorted Set 업데이트)
        String key = AUCTION_KEY_PREFIX + auctionId;
        LocalDateTime newEndTime = LocalDateTime.now().plusSeconds(seconds);
        long newEndTimeMs = newEndTime.atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        redisTemplate.opsForHash().put(key, "scheduledEndTime", newEndTime.toString());
        redisTemplate.opsForHash().put(key, "scheduledEndTimeMs", String.valueOf(newEndTimeMs));
        auctionCachePort.addToClosingQueue(auctionId, newEndTimeMs);

        // 3. RDB도 함께 업데이트 (목록 조회 시 정확한 시간 표시를 위해)
        auction.updateScheduledEndTime(newEndTime);
        auctionRepository.save(auction);

        log.info("[TEST] 경매 종료 시간 변경: auctionId={}, newEndTime={}, seconds={}", auctionId, newEndTime, seconds);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "auctionId", auctionId,
                "newScheduledEndTime", newEndTime.toString(),
                "seconds", seconds,
                "message", String.format("경매 종료 시간이 %d초 후로 변경되었습니다.", seconds)
        )));
    }

    /**
     * 경매 강제 종료 (종료 처리 스케줄러 즉시 실행)
     *
     * @param auctionId 경매 ID
     * @return 처리 결과
     */
    @PostMapping("/auctions/{auctionId}/force-close")
    public ResponseEntity<ApiResponse<Map<String, Object>>> forceClose(
            @PathVariable Long auctionId
    ) {
        // 1. DB에서 경매 조회
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> AuctionNotFoundException.withId(auctionId));

        // 2. 종료 시간을 과거로 설정 (Redis Hash + Sorted Set 업데이트)
        String key = AUCTION_KEY_PREFIX + auctionId;
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(1);
        long pastTimeMs = pastTime.atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        redisTemplate.opsForHash().put(key, "scheduledEndTime", pastTime.toString());
        redisTemplate.opsForHash().put(key, "scheduledEndTimeMs", String.valueOf(pastTimeMs));
        auctionCachePort.addToClosingQueue(auctionId, pastTimeMs);

        // 3. RDB도 함께 업데이트
        auction.updateScheduledEndTime(pastTime);
        auctionRepository.save(auction);

        // 4. 스케줄러 즉시 실행
        closeAuctionUseCase.closeExpiredAuctions();

        log.info("[TEST] 경매 강제 종료: auctionId={}", auctionId);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "auctionId", auctionId,
                "message", "경매가 강제 종료되었습니다."
        )));
    }

    /**
     * Redis 캐시 새로고침 (RDB -> Redis)
     *
     * @param auctionId 경매 ID
     * @return 처리 결과
     */
    @PostMapping("/auctions/{auctionId}/refresh-cache")
    public ResponseEntity<ApiResponse<Map<String, Object>>> refreshCache(
            @PathVariable Long auctionId
    ) {
        // 1. DB에서 경매 조회
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> AuctionNotFoundException.withId(auctionId));

        // 2. Redis 캐시 갱신
        auctionCachePort.saveToCache(auction);

        log.info("[TEST] 캐시 새로고침: auctionId={}", auctionId);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "auctionId", auctionId,
                "message", "캐시가 새로고침되었습니다."
        )));
    }
}
