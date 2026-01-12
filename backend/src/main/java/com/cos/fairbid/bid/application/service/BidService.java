package com.cos.fairbid.bid.application.service;

import com.cos.fairbid.auction.application.port.out.AuctionRepository;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.exception.AuctionNotFoundException;
import com.cos.fairbid.bid.application.port.in.PlaceBidUseCase;
import com.cos.fairbid.bid.application.port.out.BidRepository;
import com.cos.fairbid.bid.domain.Bid;
import com.cos.fairbid.bid.domain.BidType;
import com.cos.fairbid.bid.domain.event.BidPlacedEvent;
import com.cos.fairbid.bid.domain.exception.InvalidBidException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 입찰 유스케이스 구현체
 *
 * 입찰 처리 흐름:
 * 1. 비관적 락으로 경매 조회
 * 2. 입찰 자격 검증 (종료 여부, 본인 경매 여부)
 * 3. 연장 구간 확인 및 연장 처리
 * 4. 입찰 금액 결정
 * 5. 입찰 처리 (현재가 갱신)
 * 6. 경매 상태 저장
 * 7. 입찰 이력 저장
 * 8. 이벤트 발행 (실시간 UI 업데이트)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidService implements PlaceBidUseCase {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 입찰을 처리한다
     *
     * @param command 입찰 명령
     * @return 생성된 입찰 도메인 객체
     */
    @Override
    @Transactional
    public Bid placeBid(PlaceBidCommand command) {
        // 1. 비관적 락으로 경매 조회 (동시성 제어)
        Auction auction = auctionRepository.findByIdWithLock(command.auctionId())
                .orElseThrow(() -> AuctionNotFoundException.withId(command.auctionId()));

        // 2. 입찰 자격 검증 (경매 종료 여부, 본인 경매 여부)
        auction.validateBidEligibility(command.bidderId());

        // 3. 연장 구간 확인 및 처리
        boolean extended = false;
        if (auction.isInExtensionPeriod()) {
            auction.extend();
            extended = true;
        }

        // 4. 입찰 금액 결정 (원터치 입찰의 경우 최소 입찰가 자동 적용)
        Long bidAmount = determineBidAmount(command, auction);

        // 5. 입찰 처리 (현재가 갱신, 입찰수 증가, 입찰단위 재계산)
        auction.placeBid(bidAmount);

        // 6. 경매 상태 저장
        auctionRepository.save(auction);

        // 7. 입찰 이력 생성 및 저장
        Bid bid = Bid.create(
                command.auctionId(),
                command.bidderId(),
                bidAmount,
                command.bidType()
        );
        Bid savedBid = bidRepository.save(bid);

        // 8. 이벤트 발행 (실시간 UI 업데이트용)
        publishBidPlacedEvent(auction, extended);

        return savedBid;
    }

    /**
     * 입찰 금액을 결정한다
     * - ONE_TOUCH: 최소 입찰 금액 자동 적용
     * - DIRECT: 사용자 지정 금액 사용
     *
     * @param command 입찰 명령
     * @param auction 경매 도메인
     * @return 입찰 금액
     */
    private Long determineBidAmount(PlaceBidCommand command, Auction auction) {
        if (command.bidType() == BidType.ONE_TOUCH) {
            // 원터치 입찰: 현재가 + 할증된 입찰단위
            return auction.getMinBidAmount();
        }

        // 금액 직접 지정: amount 필수 검증
        if (command.amount() == null) {
            throw InvalidBidException.amountRequiredForDirectBid();
        }
        return command.amount();
    }

    /**
     * 입찰 완료 이벤트를 발행한다
     * 실시간 UI 업데이트용 (현재가, 종료시간, 다음 입찰가, 입찰 단위, 총 입찰수)
     *
     * @param auction  경매 도메인
     * @param extended 연장 여부
     */
    private void publishBidPlacedEvent(Auction auction, boolean extended) {
        BidPlacedEvent event = BidPlacedEvent.of(
                auction.getId(),
                auction.getCurrentPrice(),
                auction.getScheduledEndTime(),
                extended,
                auction.getNextMinBidPrice(),
                auction.getBidIncrement(),
                auction.getTotalBidCount()
        );
        eventPublisher.publishEvent(event);
    }
}
