package com.cos.fairbid.bid.application.service;

import com.cos.fairbid.auction.application.port.out.AuctionRepository;
import com.cos.fairbid.bid.application.port.out.BidCachePort.BidResult;
import com.cos.fairbid.bid.application.port.out.BidEventPublisher;
import com.cos.fairbid.bid.application.port.out.BidRepository;
import com.cos.fairbid.bid.domain.Bid;
import com.cos.fairbid.bid.domain.BidType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입찰 비동기 처리 서비스
 * DB 영속화 및 이벤트 발행을 비동기로 처리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BidAsyncService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final BidEventPublisher bidEventPublisher;

    /**
     * DB 영속화 (비동기)
     * 경매 상태 업데이트 + 입찰 이력 저장 + 이벤트 발행
     *
     * @param auctionId 경매 ID
     * @param bidderId 입찰자 ID
     * @param bidAmount 입찰 금액
     * @param bidType 입찰 유형
     * @param result Lua 스크립트 실행 결과
     */
    @Async("bidAsyncExecutor")
    @Transactional
    public void syncToDatabase(
            Long auctionId,
            Long bidderId,
            Long bidAmount,
            BidType bidType,
            BidResult result
    ) {
        try {
            // 1. 경매 상태 업데이트
            auctionRepository.updateCurrentPrice(
                    auctionId,
                    result.newCurrentPrice(),
                    result.newTotalBidCount(),
                    result.newBidIncrement()
            );

            // 2. 입찰 이력 저장
            Bid bid = Bid.create(auctionId, bidderId, bidAmount, bidType);
            bidRepository.save(bid);

            // 3. 이벤트 발행 (BidResult 기반, 최신 값 사용)
            bidEventPublisher.publishBidPlaced(auctionId, result);

            log.debug("입찰 DB 영속화 완료: auctionId={}, bidderId={}, amount={}",
                    auctionId, bidderId, bidAmount);
        } catch (Exception e) {
            // 비동기 처리 실패 로깅 (추후 재처리 로직 추가 가능)
            log.error("입찰 DB 영속화 실패: auctionId={}, bidderId={}, error={}",
                    auctionId, bidderId, e.getMessage(), e);
        }
    }

    /**
     * 입찰 이력 저장 및 이벤트 발행 (비동기)
     *
     * @param auctionId 경매 ID
     * @param bidderId 입찰자 ID
     * @param bidAmount 입찰 금액
     * @param bidType 입찰 유형
     * @param result Lua 스크립트 실행 결과
     */
    @Async("bidAsyncExecutor")
    @Transactional
    public void saveBidAndPublishEvent(
            Long auctionId,
            Long bidderId,
            Long bidAmount,
            BidType bidType,
            BidResult result
    ) {
        try {
            // 1. 입찰 이력 저장
            Bid bid = Bid.create(auctionId, bidderId, bidAmount, bidType);
            bidRepository.save(bid);

            // 2. 이벤트 발행 (BidResult 기반, 최신 값 사용)
            bidEventPublisher.publishBidPlaced(auctionId, result);

            log.debug("입찰 비동기 처리 완료: auctionId={}, bidderId={}, amount={}",
                    auctionId, bidderId, bidAmount);
        } catch (Exception e) {
            // 비동기 처리 실패 로깅 (추후 재처리 로직 추가 가능)
            log.error("입찰 비동기 처리 실패: auctionId={}, bidderId={}, error={}",
                    auctionId, bidderId, e.getMessage(), e);
        }
    }
}
