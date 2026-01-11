package com.cos.fairbid.winning.application.service;

import com.cos.fairbid.auction.application.port.out.AuctionRepository;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.bid.application.port.out.BidRepository;
import com.cos.fairbid.bid.domain.Bid;
import com.cos.fairbid.notification.application.port.out.AuctionBroadcastPort;
import com.cos.fairbid.notification.application.port.out.PushNotificationPort;
import com.cos.fairbid.winning.application.port.out.WinningRepository;
import com.cos.fairbid.winning.domain.Winning;
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

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final WinningRepository winningRepository;
    private final AuctionBroadcastPort auctionBroadcastPort;
    private final PushNotificationPort pushNotificationPort;

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
                .orElseThrow(() -> new RuntimeException("경매를 찾을 수 없습니다: " + auctionId));

        // 1. 상위 2개 입찰 조회
        List<Bid> topBids = bidRepository.findTop2ByAuctionId(auctionId);

        // 2. 입찰이 없으면 유찰 처리
        if (topBids.isEmpty()) {
            handleNoWinner(auction);
            return;
        }

        // 3. 1순위 낙찰자 결정
        Bid firstBid = topBids.get(0);
        handleFirstRankWinner(auction, firstBid);

        // 4. 2순위 후보 저장 (있는 경우)
        if (topBids.size() > 1) {
            Bid secondBid = topBids.get(1);
            saveSecondRankCandidate(auction, secondBid);
        }

        // 5. WebSocket 종료 브로드캐스트
        broadcastAuctionClosed(auction);

        log.info("경매 종료 완료 - auctionId: {}, winnerId: {}", auctionId, firstBid.getBidderId());
    }

    /**
     * 입찰자가 없는 경우 유찰 처리
     */
    private void handleNoWinner(Auction auction) {
        // 경매 유찰 처리
        auction.fail();
        auctionRepository.save(auction);

        // WebSocket 종료 브로드캐스트
        broadcastAuctionClosed(auction);

        // 판매자에게 유찰 알림
        pushNotificationPort.sendFailedAuctionNotification(
                auction.getSellerId(),
                auction.getId(),
                auction.getTitle()
        );

        log.info("경매 유찰 처리 완료 - auctionId: {}", auction.getId());
    }

    /**
     * 1순위 낙찰자 처리
     */
    private void handleFirstRankWinner(Auction auction, Bid firstBid) {
        // 1. 경매 종료 및 낙찰자 지정
        auction.close(firstBid.getBidderId());
        auctionRepository.save(auction);

        // 2. 1순위 Winning 저장
        Winning firstWinning = Winning.createFirstRank(
                auction.getId(),
                firstBid.getBidderId(),
                firstBid.getAmount()
        );
        winningRepository.save(firstWinning);

        // 3. 1순위 낙찰자에게 Push 알림
        pushNotificationPort.sendWinningNotification(
                firstBid.getBidderId(),
                auction.getId(),
                auction.getTitle(),
                firstBid.getAmount()
        );
    }

    /**
     * 2순위 후보 저장
     */
    private void saveSecondRankCandidate(Auction auction, Bid secondBid) {
        Winning secondWinning = Winning.createSecondRank(
                auction.getId(),
                secondBid.getBidderId(),
                secondBid.getAmount()
        );
        winningRepository.save(secondWinning);

        log.debug("2순위 후보 저장 - auctionId: {}, bidderId: {}", auction.getId(), secondBid.getBidderId());
    }

    /**
     * 경매 종료 WebSocket 브로드캐스트
     */
    private void broadcastAuctionClosed(Auction auction) {
        try {
            auctionBroadcastPort.broadcastAuctionClosed(auction.getId());
        } catch (Exception e) {
            log.error("경매 종료 브로드캐스트 실패 - auctionId: {}", auction.getId(), e);
        }
    }
}
