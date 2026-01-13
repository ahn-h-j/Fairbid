package com.cos.fairbid.winning.domain.service;

import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.bid.domain.Bid;
import com.cos.fairbid.notification.application.port.out.PushNotificationPort;
import com.cos.fairbid.winning.application.port.out.WinningRepository;
import com.cos.fairbid.winning.domain.Winning;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 경매 종료 처리 도메인 서비스
 * 경매 종료 시 발생하는 비즈니스 로직을 담당
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionClosingProcessor {

    private final WinningRepository winningRepository;
    private final PushNotificationPort pushNotificationPort;

    /**
     * 입찰자가 없는 경우 유찰 처리한다
     *
     * @param auction 유찰 처리할 경매
     */
    public void processNoWinner(Auction auction) {
        // 경매 유찰 처리
        auction.fail();

        // 판매자에게 유찰 알림
        pushNotificationPort.sendFailedAuctionNotification(
                auction.getSellerId(),
                auction.getId(),
                auction.getTitle()
        );

        log.info("경매 유찰 처리 완료 - auctionId: {}", auction.getId());
    }

    /**
     * 1순위 낙찰자를 처리한다
     *
     * @param auction  종료할 경매
     * @param firstBid 1순위 입찰
     */
    public void processFirstRankWinner(Auction auction, Bid firstBid) {
        // 1. 경매 종료 및 낙찰자 지정
        auction.close(firstBid.getBidderId());

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
     * 2순위 후보를 저장한다
     *
     * @param auction   경매
     * @param secondBid 2순위 입찰
     */
    public void saveSecondRankCandidate(Auction auction, Bid secondBid) {
        Winning secondWinning = Winning.createSecondRank(
                auction.getId(),
                secondBid.getBidderId(),
                secondBid.getAmount()
        );
        winningRepository.save(secondWinning);

        log.debug("2순위 후보 저장 - auctionId: {}, bidderId: {}", auction.getId(), secondBid.getBidderId());
    }
}
