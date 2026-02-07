package com.cos.fairbid.bid.application.service;

import com.cos.fairbid.auction.application.port.out.AuctionRepositoryPort;
import com.cos.fairbid.bid.application.port.out.BidRepositoryPort;
import com.cos.fairbid.bid.domain.Bid;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입찰 이력 비동기 RDB 저장 서비스
 *
 * BidService에서 분리하여 @Async로 RDB 저장을 비동기 처리한다.
 * 이를 통해 DB 장애가 입찰 API 응답에 전파되지 않도록 장애 격리를 달성한다.
 *
 * 한계:
 * - 메모리 기반 큐이므로 앱 강제 종료 시 대기 중인 작업이 유실된다.
 * - 이 한계는 Phase 3(MQ)에서 해결한다.
 */
@Service
@Slf4j
public class BidRdbSyncService {

    private final BidRepositoryPort bidRepository;
    private final AuctionRepositoryPort auctionRepository;
    private final Timer rdbSyncTimer;

    public BidRdbSyncService(
            BidRepositoryPort bidRepository,
            AuctionRepositoryPort auctionRepository,
            MeterRegistry meterRegistry
    ) {
        this.bidRepository = bidRepository;
        this.auctionRepository = auctionRepository;
        // BidService와 동일한 메트릭 이름 → Micrometer가 같은 인스턴스 반환
        this.rdbSyncTimer = Timer.builder("fairbid_bid_rdb_sync_seconds")
                .description("RDB 동기화 소요 시간")
                .publishPercentileHistogram(true)
                .register(meterRegistry);
    }

    /**
     * 입찰 이력 비동기 저장
     * 별도 스레드(bidAsyncExecutor)에서 실행되며, DB 장애 시에도 호출 스레드를 블로킹하지 않는다.
     */
    @Async("bidAsyncExecutor")
    @Transactional
    public void saveBidAsync(Bid bid) {
        try {
            rdbSyncTimer.record(() -> bidRepository.save(bid));
            log.debug("입찰 이력 RDB 비동기 저장 완료: auctionId={}, bidderId={}, amount={}",
                    bid.getAuctionId(), bid.getBidderId(), bid.getAmount());
        } catch (Exception e) {
            // DB 장애 시 로그만 남기고 유실 (메모리 큐 한계)
            log.error("입찰 이력 RDB 저장 실패 (유실): auctionId={}, bidderId={}, error={}",
                    bid.getAuctionId(), bid.getBidderId(), e.getMessage());
        }
    }

    /**
     * 즉시 구매 활성화 시 경매 상태 비동기 업데이트
     */
    @Async("bidAsyncExecutor")
    @Transactional
    public void updateInstantBuyActivatedAsync(
            Long auctionId, Long currentPrice, Integer totalBidCount,
            Long bidIncrement, Long bidderId, Long currentTimeMs, Long scheduledEndTimeMs
    ) {
        try {
            auctionRepository.updateInstantBuyActivated(
                    auctionId, currentPrice, totalBidCount,
                    bidIncrement, bidderId, currentTimeMs, scheduledEndTimeMs
            );
            log.info("즉시 구매 활성화 RDB 비동기 동기화: auctionId={}, instantBuyerId={}", auctionId, bidderId);
        } catch (Exception e) {
            log.error("즉시 구매 활성화 RDB 저장 실패: auctionId={}, error={}", auctionId, e.getMessage());
        }
    }
}
