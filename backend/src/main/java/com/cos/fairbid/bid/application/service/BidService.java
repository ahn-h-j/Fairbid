package com.cos.fairbid.bid.application.service;

import com.cos.fairbid.auction.application.port.out.AuctionCachePort;
import com.cos.fairbid.auction.application.port.out.AuctionRepository;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.exception.AuctionNotFoundException;
import com.cos.fairbid.bid.application.port.in.PlaceBidUseCase;
import com.cos.fairbid.bid.application.port.out.BidCachePort;
import com.cos.fairbid.bid.application.port.out.BidCachePort.BidResult;
import com.cos.fairbid.bid.application.port.out.BidEventPublisher;
import com.cos.fairbid.bid.domain.Bid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 입찰 서비스
 * Redis 메인 DB + Lua 스크립트로 원자적 입찰 처리
 *
 * 흐름:
 * 1. Redis 캐시 확인 (없으면 RDB에서 로드)
 * 2. Lua 스크립트로 원자적 입찰 처리 (Read + Write)
 * 3. RDB 동기화 모킹 (추후 MQ로 비동기 처리)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BidService implements PlaceBidUseCase {

    private final BidCachePort bidCachePort;
    private final AuctionRepository auctionRepository;
    private final AuctionCachePort auctionCachePort;
    private final BidEventPublisher bidEventPublisher;

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
        bidEventPublisher.publishBidPlaced(command.auctionId(), result);

        // 5. RDB 동기화 모킹
        mockRdbSync(command, bid, result);

        return bid;
    }

    /**
     * RDB 동기화 모킹 (실제 저장 X)
     */
    private void mockRdbSync(PlaceBidCommand command, Bid bid, BidResult result) {
        log.info("[MOCK] RDB 동기화 데이터 - auctionId={}, bidderId={}, bidAmount={}, totalBidCount={}, bidIncrement={}",
                command.auctionId(), command.bidderId(), bid.getAmount(), result.newTotalBidCount(), result.newBidIncrement());
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
