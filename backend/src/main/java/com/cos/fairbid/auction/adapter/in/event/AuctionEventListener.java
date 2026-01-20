package com.cos.fairbid.auction.adapter.in.event;

import com.cos.fairbid.auction.application.port.out.AuctionCachePort;
import com.cos.fairbid.auction.domain.event.AuctionCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 경매 도메인 이벤트 리스너
 * 트랜잭션 커밋 후 캐시 워밍 등 후처리 수행
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuctionEventListener {

    private final AuctionCachePort auctionCachePort;

    /**
     * 경매 생성 후 Redis 캐시 워밍
     * 트랜잭션 커밋 후 실행되어 RDB-Redis 일관성 보장
     * 실패 시 BidService의 cache-aside가 fallback으로 동작
     *
     * @param event 경매 생성 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAuctionCreated(AuctionCreatedEvent event) {
        try {
            auctionCachePort.saveToCache(event.getAuction());
            log.info("캐시 워밍 완료: auctionId={}", event.getAuction().getId());
        } catch (Exception e) {
            // 캐시 워밍 실패해도 입찰 시 cache-aside로 복구됨
            log.warn("캐시 워밍 실패 (fallback 가능): auctionId={}, error={}",
                    event.getAuction().getId(), e.getMessage());
        }
    }
}
