package com.cos.fairbid.auction.application.service;

import com.cos.fairbid.auction.application.port.in.CreateAuctionUseCase;
import com.cos.fairbid.auction.application.port.in.GetAuctionDetailUseCase;
import com.cos.fairbid.auction.application.port.in.GetAuctionListUseCase;
import com.cos.fairbid.auction.application.port.in.GetUserWinningInfoUseCase;
import com.cos.fairbid.auction.application.port.out.AuctionCachePort;
import com.cos.fairbid.auction.application.port.out.AuctionRepositoryPort;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.auction.domain.event.AuctionCreatedEvent;
import com.cos.fairbid.auction.domain.exception.AuctionNotFoundException;
import com.cos.fairbid.winning.application.port.out.WinningRepositoryPort;
import com.cos.fairbid.winning.domain.Winning;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경매 유스케이스 구현체
 *
 * Redis가 메인 DB이며, 경매 상세 조회 시 Redis에서 먼저 조회한다.
 * 캐시 미스 시 RDB에서 조회 후 캐시를 워밍한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class AuctionService implements CreateAuctionUseCase, GetAuctionDetailUseCase, GetAuctionListUseCase, GetUserWinningInfoUseCase {

    private final AuctionRepositoryPort auctionRepository;
    private final AuctionCachePort auctionCachePort;
    private final ApplicationEventPublisher eventPublisher;
    private final WinningRepositoryPort winningRepositoryPort;

    /**
     * 새로운 경매를 생성한다
     *
     * 1. 도메인 객체 생성 (비즈니스 검증 포함)
     * 2. 저장소에 저장
     *
     * @param command 경매 생성 명령
     * @return 생성된 경매 도메인 객체
     */
    @Override
    @Transactional
    public Auction createAuction(CreateAuctionCommand command) {
        // 도메인 객체 생성 (Auction.create에서 비즈니스 검증 수행)
        Auction auction = Auction.create(
                command.sellerId(),
                command.title(),
                command.description(),
                command.category(),
                command.startPrice(),
                command.instantBuyPrice(),
                command.duration(),
                command.imageUrls(),
                command.directTradeAvailable(),
                command.deliveryAvailable(),
                command.directTradeLocation()
        );

        // 저장
        Auction saved = auctionRepository.save(auction);

        // 이벤트 발행 (AfterCommit 리스너에서 캐시 워밍)
        eventPublisher.publishEvent(AuctionCreatedEvent.of(saved));

        return saved;
    }

    /**
     * 경매 상세 정보를 조회한다
     *
     * Redis가 메인 DB이므로 Redis에서 먼저 조회한다.
     * 캐시 미스 시 RDB에서 조회 후 캐시를 워밍한다.
     *
     * @param auctionId 조회할 경매 ID
     * @return 경매 도메인 객체
     * @throws AuctionNotFoundException 경매가 존재하지 않을 경우
     */
    @Override
    public Auction getAuctionDetail(Long auctionId) {
        // 1. Redis에서 먼저 조회 (메인 DB)
        return auctionCachePort.findById(auctionId)
                .orElseGet(() -> {
                    // 2. 캐시 미스 시 RDB에서 조회 후 캐시 워밍
                    log.info("경매 캐시 미스, RDB에서 조회 후 캐시 워밍: auctionId={}", auctionId);
                    Auction auction = auctionRepository.findById(auctionId)
                            .orElseThrow(() -> AuctionNotFoundException.withId(auctionId));
                    auctionCachePort.saveToCache(auction);
                    return auction;
                });
    }

    /**
     * 경매 목록을 조회한다
     *
     * @param status   경매 상태 필터 (nullable)
     * @param keyword  검색어 - 상품명 (nullable)
     * @param pageable 페이지네이션 정보
     * @return 경매 목록 (페이지)
     */
    @Override
    public Page<Auction> getAuctionList(AuctionStatus status, String keyword, Pageable pageable) {
        return auctionRepository.findAll(status, keyword, pageable);
    }

    /**
     * 특정 경매에서 사용자의 낙찰 정보를 조회한다.
     *
     * @param auctionId 경매 ID
     * @param userId    사용자 ID
     * @return 사용자의 낙찰 정보 (낙찰자가 아니면 null)
     */
    @Override
    public UserWinningInfo getUserWinningInfo(Long auctionId, Long userId) {
        if (userId == null) {
            return null;
        }

        Winning userWinning = winningRepositoryPort.findByAuctionId(auctionId).stream()
                .filter(w -> w.getBidderId().equals(userId))
                .findFirst()
                .orElse(null);

        if (userWinning == null) {
            return null;
        }

        return new UserWinningInfo(
                userWinning.getRank(),
                userWinning.getStatus().name()
        );
    }
}
