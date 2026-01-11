package com.cos.fairbid.winning.application.service;

import com.cos.fairbid.auction.application.port.out.AuctionRepository;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.exception.AuctionNotFoundException;
import com.cos.fairbid.notification.application.port.out.PushNotificationPort;
import com.cos.fairbid.winning.application.port.out.WinningRepository;
import com.cos.fairbid.winning.domain.Winning;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 노쇼 처리 헬퍼
 * 개별 Winning 노쇼 처리를 별도 트랜잭션에서 실행
 *
 * REQUIRES_NEW 전파를 사용하여 각 노쇼 처리가 독립적인 트랜잭션에서 실행되도록 함
 * 이로써 하나의 노쇼 처리 실패가 다른 처리에 영향을 주지 않음
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoShowProcessingHelper {

    private final WinningRepository winningRepository;
    private final AuctionRepository auctionRepository;
    private final PushNotificationPort pushNotificationPort;

    /**
     * 단일 Winning 노쇼 처리
     * 독립적인 트랜잭션에서 실행 (REQUIRES_NEW)
     *
     * @param winningId 처리 대상 Winning ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processExpiredWinning(Long winningId) {
        // 새 트랜잭션에서 Winning 다시 조회
        Winning winning = winningRepository.findById(winningId)
                .orElseThrow(() -> {
                    log.warn("Winning을 찾을 수 없습니다: {}", winningId);
                    return new IllegalStateException("Winning을 찾을 수 없습니다: " + winningId);
                });

        Long auctionId = winning.getAuctionId();
        Auction auction = auctionRepository.findById(auctionId)
                .orElseThrow(() -> AuctionNotFoundException.withId(auctionId));

        if (winning.getRank() == 1) {
            // 1순위 노쇼 처리
            processFirstRankNoShow(winning, auction);
        } else {
            // 2순위 노쇼 (승계 후 미결제) → 유찰 처리 (노쇼 처리 안함)
            processSecondRankExpired(winning, auction);
        }
    }

    /**
     * 1순위 노쇼 처리
     */
    private void processFirstRankNoShow(Winning firstWinning, Auction auction) {
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
        handleAuctionFailed(auction);
    }

    /**
     * 2순위 승계 처리
     */
    private void transferToSecondRank(Winning secondWinning, Auction auction) {
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
     * 2순위 만료 처리 (승계 후 미결제)
     * 비즈니스 규칙: 2순위 승계 후 미결제는 노쇼 처리 안함
     */
    private void processSecondRankExpired(Winning secondWinning, Auction auction) {
        // 상태만 FAILED로 변경 (노쇼 처리 안함)
        secondWinning.markAsFailed();
        winningRepository.save(secondWinning);

        // 경매 유찰 처리
        handleAuctionFailed(auction);

        log.info("2순위 만료 → 유찰 처리 - auctionId: {}", auction.getId());
    }

    /**
     * 경매 유찰 처리
     */
    private void handleAuctionFailed(Auction auction) {
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
