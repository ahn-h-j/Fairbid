package com.cos.fairbid.bid.application.service;

import com.cos.fairbid.auction.application.port.out.AuctionCachePort;
import com.cos.fairbid.auction.application.port.out.AuctionRepositoryPort;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.exception.AuctionNotFoundException;
import com.cos.fairbid.bid.application.port.in.PlaceBidUseCase;
import com.cos.fairbid.bid.application.port.out.BidCachePort;
import com.cos.fairbid.bid.application.port.out.BidCachePort.BidResult;
import com.cos.fairbid.bid.application.port.out.BidEventPublisherPort;
import com.cos.fairbid.bid.domain.Bid;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 입찰 서비스
 * Redis 메인 DB + Lua 스크립트로 원자적 입찰 처리
 *
 * 흐름:
 * 1. Redis 캐시 확인 (없으면 RDB에서 로드)
 * 2. Lua 스크립트로 원자적 입찰 처리 (Read + Write)
 * 3. WebSocket 이벤트 발행
 * 4. RDB 비동기 저장 (@Async → BidRdbSyncService)
 *
 * @Transactional 제거:
 * 기존에는 클래스 레벨 @Transactional로 인해 placeBid() 진입 시점에 DB 커넥션을 획득했다.
 * 이로 인해 DB 장애 시 Redis 작업조차 블로킹되는 "장애 전파" 문제가 있었다.
 * RDB 저장을 BidRdbSyncService(@Async)로 분리하여 장애 격리를 달성한다.
 */
@Service
@Slf4j
public class BidService implements PlaceBidUseCase {

    private final BidCachePort bidCachePort;
    private final AuctionRepositoryPort auctionRepository;
    private final AuctionCachePort auctionCachePort;
    private final BidEventPublisherPort bidEventPublisher;
    private final BidRdbSyncService bidRdbSyncService;

    /** 입찰 성공 카운터 */
    private final Counter bidSuccessCounter;
    /** 입찰 실패 카운터 */
    private final Counter bidFailCounter;

    public BidService(
            BidCachePort bidCachePort,
            AuctionRepositoryPort auctionRepository,
            AuctionCachePort auctionCachePort,
            BidEventPublisherPort bidEventPublisher,
            BidRdbSyncService bidRdbSyncService,
            MeterRegistry meterRegistry
    ) {
        this.bidCachePort = bidCachePort;
        this.auctionRepository = auctionRepository;
        this.auctionCachePort = auctionCachePort;
        this.bidEventPublisher = bidEventPublisher;
        this.bidRdbSyncService = bidRdbSyncService;

        // Micrometer 메트릭 등록
        this.bidSuccessCounter = Counter.builder("fairbid_bid_total")
                .tag("result", "success")
                .description("입찰 성공 건수")
                .register(meterRegistry);
        this.bidFailCounter = Counter.builder("fairbid_bid_total")
                .tag("result", "fail")
                .description("입찰 실패 건수")
                .register(meterRegistry);
    }

    @Override
    public Bid placeBid(PlaceBidCommand command) {
        // 1. 캐시 확인 (없으면 RDB에서 로드)
        if (!bidCachePort.existsInCache(command.auctionId())) {
            loadAuctionToRedis(command.auctionId());
        }

        // 2. Lua 스크립트로 원자적 입찰 처리 (현재 시간 전달)
        long currentTimeMs = System.currentTimeMillis();
        BidResult result = bidCachePort.placeBidAtomic(
                command.auctionId(),
                command.amount() != null ? command.amount() : 0L,
                command.bidderId(),
                command.bidType().name(),
                currentTimeMs
        );

        log.debug("입찰 성공 (Redis Lua): auctionId={}, bidAmount={}, totalBidCount={}",
                command.auctionId(), result.newCurrentPrice(), result.newTotalBidCount());

        // 3. Bid 도메인 생성
        Bid bid = Bid.create(command.auctionId(), command.bidderId(), result.newCurrentPrice(), command.bidType());

        // 4. 웹소켓 이벤트 발행 (실시간 알림) - BidResult에서 최신 값 사용
        // 입찰 성공한 사람이 곧 1순위 입찰자(topBidderId)
        bidEventPublisher.publishBidPlaced(command.auctionId(), result, command.bidderId());

        // 5. 비동기 RDB 저장 (별도 스레드, DB 장애 시에도 여기서 블로킹되지 않음)
        bidRdbSyncService.saveBidAsync(bid);

        // 6. 즉시 구매 활성화 시에만 경매 상태 비동기 업데이트
        if (Boolean.TRUE.equals(result.instantBuyActivated())) {
            bidRdbSyncService.updateInstantBuyActivatedAsync(
                    command.auctionId(),
                    result.newCurrentPrice(),
                    result.newTotalBidCount(),
                    result.newBidIncrement(),
                    command.bidderId(),
                    currentTimeMs,
                    result.scheduledEndTimeMs()
            );
        }

        bidSuccessCounter.increment();
        return bid;
    }

    /**
     * 캐시 미스 시 RDB에서 경매 정보를 조회하여 Redis에 로드
     *
     * @param auctionId 경매 ID
     * @return 로드된 경매 도메인 객체
     */
    private Auction loadAuctionToRedis(Long auctionId) {
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> AuctionNotFoundException.withId(auctionId));
        auctionCachePort.saveToCache(auction);
        log.info("캐시 미스, RDB에서 Redis로 로드: auctionId={}", auctionId);
        return auction;
    }
}
