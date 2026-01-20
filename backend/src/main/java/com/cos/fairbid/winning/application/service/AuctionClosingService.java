package com.cos.fairbid.winning.application.service;

import com.cos.fairbid.auction.application.port.out.AuctionRepositoryPort;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.winning.application.port.in.CloseAuctionUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 경매 종료 서비스
 *
 * 경매 종료 처리 흐름:
 * 1. 종료 대상 경매 조회
 * 2. 각 경매를 별도 트랜잭션에서 종료 처리 (AuctionClosingHelper 위임)
 *
 * 개별 경매 처리는 REQUIRES_NEW 트랜잭션으로 독립 실행되어
 * 하나의 실패가 다른 경매에 영향을 주지 않음
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionClosingService implements CloseAuctionUseCase {

    private final AuctionRepositoryPort auctionRepository;
    private final AuctionClosingHelper auctionClosingHelper;

    @Override
    public void closeExpiredAuctions() {
        // 1. 종료 대상 경매 조회
        List<Auction> closingAuctions = auctionRepository.findClosingAuctions();

        if (closingAuctions.isEmpty()) {
            return;
        }

        log.info("종료 대상 경매 {}건 처리 시작", closingAuctions.size());

        // 2. 각 경매 종료 처리 (별도 트랜잭션에서 실행)
        for (Auction auction : closingAuctions) {
            try {
                auctionClosingHelper.processAuctionClosing(auction.getId());
            } catch (Exception e) {
                // 개별 경매 실패는 로그만 남기고 계속 진행
                log.error("경매 종료 처리 실패 - auctionId: {}, error: {}", auction.getId(), e.getMessage());
            }
        }

        log.info("종료 대상 경매 {}건 처리 완료", closingAuctions.size());
    }
}
