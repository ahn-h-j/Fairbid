package com.cos.fairbid.bid.application.service;

import com.cos.fairbid.auction.application.port.out.AuctionCachePort;
import com.cos.fairbid.auction.application.port.out.AuctionRepositoryPort;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.exception.AuctionNotFoundException;
import com.cos.fairbid.bid.application.port.in.PlaceBidUseCase;
import com.cos.fairbid.bid.application.port.out.BidCachePort;
import com.cos.fairbid.bid.application.port.out.BidCachePort.BidResult;
import com.cos.fairbid.bid.application.port.out.BidEventPublisherPort;
import com.cos.fairbid.bid.application.port.out.BidRepositoryPort;
import com.cos.fairbid.bid.domain.Bid;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입찰 서비스
 * Redis 메인 DB + Lua 스크립트로 원자적 입찰 처리
 *
 * 흐름:
 * 1. Redis 캐시 확인 (없으면 RDB에서 로드)
 * 2. Lua 스크립트로 원자적 입찰 처리 (Read + Write)
 * 3. RDB 동기화 (경매 목록 페이지용)
 */
@Service
@Slf4j
@Transactional
public class BidService implements PlaceBidUseCase {

    private final BidCachePort bidCachePort;
    private final BidRepositoryPort bidRepository;
    private final AuctionRepositoryPort auctionRepository;
    private final AuctionCachePort auctionCachePort;
    private final BidEventPublisherPort bidEventPublisher;

    /** 입찰 성공 카운터 */
    private final Counter bidSuccessCounter;
    /** 입찰 실패 카운터 */
    private final Counter bidFailCounter;
    /** RDB 동기화 소요 시간 */
    private final Timer rdbSyncTimer;

    public BidService(
            BidCachePort bidCachePort,
            BidRepositoryPort bidRepository,
            AuctionRepositoryPort auctionRepository,
            AuctionCachePort auctionCachePort,
            BidEventPublisherPort bidEventPublisher,
            MeterRegistry meterRegistry
    ) {
        this.bidCachePort = bidCachePort;
        this.bidRepository = bidRepository;
        this.auctionRepository = auctionRepository;
        this.auctionCachePort = auctionCachePort;
        this.bidEventPublisher = bidEventPublisher;

        // Micrometer 메트릭 등록
        this.bidSuccessCounter = Counter.builder("fairbid_bid_total")
                .tag("result", "success")
                .description("입찰 성공 건수")
                .register(meterRegistry);
        this.bidFailCounter = Counter.builder("fairbid_bid_total")
                .tag("result", "fail")
                .description("입찰 실패 건수")
                .register(meterRegistry);
        this.rdbSyncTimer = Timer.builder("fairbid_bid_rdb_sync_seconds")
                .description("RDB 동기화 소요 시간")
                .publishPercentileHistogram(true)  // Prometheus histogram bucket 생성 (histogram_quantile 사용 가능)
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

        // 5. 즉시 구매 활성화 시에만 RDB 동기화 (상태 변경이 필요한 경우)
        // 일반 입찰 시에는 auction 테이블 UPDATE 하지 않음 (성능 최적화)
        // 경매 목록의 currentPrice는 Redis에서 조회 (AuctionService.getAuctionList)
        if (Boolean.TRUE.equals(result.instantBuyActivated())) {
            auctionRepository.updateInstantBuyActivated(
                    command.auctionId(),
                    result.newCurrentPrice(),
                    result.newTotalBidCount(),
                    result.newBidIncrement(),
                    command.bidderId(),
                    currentTimeMs,
                    result.scheduledEndTimeMs()
            );
            log.info("즉시 구매 활성화 RDB 동기화: auctionId={}, instantBuyerId={}", command.auctionId(), command.bidderId());
        }

        // 6. 입찰 이력 저장 (RDB) - Timer로 소요 시간 측정
        Bid savedBid = rdbSyncTimer.record(() -> bidRepository.save(bid));

        bidSuccessCounter.increment();
        log.debug("입찰 이력 RDB 저장 완료: auctionId={}, bidderId={}, amount={}",
                command.auctionId(), command.bidderId(), bid.getAmount());

        return savedBid;
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
