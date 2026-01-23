package com.cos.fairbid.winning.application.service;

import com.cos.fairbid.auction.application.port.out.AuctionCachePort;
import com.cos.fairbid.auction.application.port.out.AuctionRepositoryPort;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.auction.domain.exception.AuctionNotFoundException;
import com.cos.fairbid.bid.application.port.out.BidRepositoryPort;
import com.cos.fairbid.bid.domain.Bid;
import com.cos.fairbid.winning.application.port.out.AuctionClosedEventPublisherPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 경매 종료 헬퍼
 * 개별 경매 종료 처리를 별도 트랜잭션에서 실행
 *
 * REQUIRES_NEW 전파를 사용하여 각 경매 처리가 독립적인 트랜잭션에서 실행되도록 함
 * 이로써 하나의 경매 처리 실패가 다른 경매에 영향을 주지 않음
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionClosingHelper {

    private final AuctionRepositoryPort auctionRepository;
    private final AuctionCachePort auctionCachePort;
    private final BidRepositoryPort bidRepository;
    private final AuctionClosingProcessor closingProcessor;
    private final AuctionClosedEventPublisherPort eventPublisher;

    /**
     * 단일 경매 종료 처리
     * 독립적인 트랜잭션에서 실행 (REQUIRES_NEW)
     *
     * @param auctionId 종료 대상 경매 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processAuctionClosing(Long auctionId) {
        // 새 트랜잭션에서 경매 다시 조회
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> AuctionNotFoundException.withId(auctionId));

        // 1. 상위 2개 입찰 조회
        List<Bid> topBids = bidRepository.findTop2ByAuctionId(auctionId);

        // 2. 입찰이 없으면 유찰 처리
        if (topBids.isEmpty()) {
            closingProcessor.processNoWinner(auction);
            auctionRepository.save(auction);

            // Redis 캐시 상태 동기화 + 종료 대기 큐 제거
            syncRedisAfterClosing(auctionId, AuctionStatus.FAILED);

            eventPublisher.publishAuctionClosed(auctionId);
            log.info("경매 유찰 완료 - auctionId: {}", auctionId);
            return;
        }

        // 3. 1순위 낙찰자 결정
        Bid firstBid = topBids.get(0);
        closingProcessor.processFirstRankWinner(auction, firstBid);

        // 4. 2순위 후보 저장 (있는 경우)
        if (topBids.size() > 1) {
            closingProcessor.saveSecondRankCandidate(auction, topBids.get(1));
        }

        // 5. 경매 저장
        auctionRepository.save(auction);

        // 6. Redis 캐시 상태 동기화 + 종료 대기 큐 제거
        syncRedisAfterClosing(auctionId, AuctionStatus.ENDED);

        // 7. 종료 이벤트 발행
        eventPublisher.publishAuctionClosed(auctionId);

        log.info("경매 종료 완료 - auctionId: {}, winnerId: {}", auctionId, firstBid.getBidderId());
    }

    /**
     * 경매 종료 후 Redis 상태를 동기화한다
     * - 캐시의 status 필드를 업데이트하여 조회 시 정확한 상태 반환
     * - 종료 대기 큐(Sorted Set)에서 제거하여 중복 처리 방지
     *
     * @param auctionId 경매 ID
     * @param status    종료 후 상태 (ENDED / FAILED)
     */
    private void syncRedisAfterClosing(Long auctionId, AuctionStatus status) {
        try {
            auctionCachePort.updateStatus(auctionId, status);
            auctionCachePort.removeFromClosingQueue(auctionId);
        } catch (Exception e) {
            // Redis 동기화 실패는 로그만 남기고 진행 (RDB는 이미 업데이트됨)
            log.warn("Redis 동기화 실패 - auctionId: {}, error: {}", auctionId, e.getMessage());
        }
    }
}
