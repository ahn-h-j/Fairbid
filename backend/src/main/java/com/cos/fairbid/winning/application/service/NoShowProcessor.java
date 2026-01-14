package com.cos.fairbid.winning.application.service;

import com.cos.fairbid.auction.application.port.out.AuctionRepository;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.notification.application.port.out.PushNotificationPort;
import com.cos.fairbid.winning.application.port.out.WinningRepository;
import com.cos.fairbid.winning.domain.Winning;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 노쇼 처리 서비스
 * 결제 기한 만료 시 발생하는 비즈니스 로직을 담당
 *
 * Port 의존성이 있으므로 application 계층에 위치
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoShowProcessor {

    private final WinningRepository winningRepository;
    private final AuctionRepository auctionRepository;
    private final PushNotificationPort pushNotificationPort;

    /**
     * 1순위 노쇼를 처리한다
     * 노쇼 처리 후 2순위 승계 여부를 확인하여 처리한다
     *
     * @param firstWinning 1순위 낙찰 정보
     * @param auction      경매
     */
    public void processFirstRankNoShow(Winning firstWinning, Auction auction) {
        Long auctionId = auction.getId();

        // 1. 1순위 노쇼 처리
        firstWinning.markAsNoShow();
        winningRepository.save(firstWinning);

        // 2. 경고 부여 (TODO: User 도메인 구현 후)
        // userRepository.addWarning(firstWinning.getBidderId());
        log.info("1순위 노쇼 처리 - auctionId: {}, bidderId: {}", auctionId, firstWinning.getBidderId());

        // 3. 2순위 승계 여부 확인
        Optional<Winning> secondWinningOpt = winningRepository.findByAuctionIdAndRank(auctionId, 2);

        if (secondWinningOpt.isPresent()) {
            Winning secondWinning = secondWinningOpt.get();

            // 2순위가 90% 이상이면 자동 승계
            if (secondWinning.isEligibleForAutoTransfer(firstWinning.getBidAmount())) {
                transferToSecondRank(secondWinning, auction);
                return;
            }
        }

        // 4. 승계 불가 → 유찰 처리
        failAuction(auction);
    }

    /**
     * 2순위 승계를 처리한다
     *
     * @param secondWinning 2순위 낙찰 정보
     * @param auction       경매
     */
    public void transferToSecondRank(Winning secondWinning, Auction auction) {
        // 1. 2순위에게 결제 권한 부여 (1시간)
        secondWinning.transferToSecondRank();
        winningRepository.save(secondWinning);

        // 2. 경매 낙찰자 변경
        auction.transferWinner(secondWinning.getBidderId());
        auctionRepository.save(auction);

        // 3. 2순위에게 승계 알림
        pushNotificationPort.sendTransferNotification(
                secondWinning.getBidderId(),
                auction.getId(),
                auction.getTitle(),
                secondWinning.getBidAmount()
        );

        log.info("2순위 승계 완료 - auctionId: {}, newWinnerId: {}", auction.getId(), secondWinning.getBidderId());
    }

    /**
     * 2순위 만료를 처리한다 (승계 후 미결제)
     * 비즈니스 규칙: 2순위 승계 후 미결제는 노쇼 처리 안함
     *
     * @param secondWinning 2순위 낙찰 정보
     * @param auction       경매
     */
    public void processSecondRankExpired(Winning secondWinning, Auction auction) {
        // 상태만 FAILED로 변경 (노쇼 처리 안함)
        secondWinning.markAsFailed();
        winningRepository.save(secondWinning);

        // 경매 유찰 처리
        failAuction(auction);

        log.info("2순위 만료 → 유찰 처리 - auctionId: {}", auction.getId());
    }

    /**
     * 경매를 유찰 처리한다
     *
     * @param auction 유찰 처리할 경매
     */
    public void failAuction(Auction auction) {
        auction.fail();
        auctionRepository.save(auction);

        // 판매자에게 유찰 알림
        pushNotificationPort.sendFailedAuctionNotification(
                auction.getSellerId(),
                auction.getId(),
                auction.getTitle()
        );

        log.info("경매 유찰 처리 완료 - auctionId: {}", auction.getId());
    }
}
