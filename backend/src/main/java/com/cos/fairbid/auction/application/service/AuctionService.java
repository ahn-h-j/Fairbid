package com.cos.fairbid.auction.application.service;

import com.cos.fairbid.auction.application.port.in.CreateAuctionUseCase;
import com.cos.fairbid.auction.application.port.in.GetAuctionDetailUseCase;
import com.cos.fairbid.auction.application.port.out.AuctionRepository;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.auction.domain.exception.AuctionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경매 유스케이스 구현체
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionService implements CreateAuctionUseCase, GetAuctionDetailUseCase {

    private final AuctionRepository auctionRepository;

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
                command.imageUrls()
        );

        // 저장 후 반환
        return auctionRepository.save(auction);
    }

    /**
     * 경매 상세 정보를 조회한다
     *
     * @param auctionId 조회할 경매 ID
     * @return 경매 도메인 객체
     * @throws AuctionNotFoundException 경매가 존재하지 않을 경우
     */
    @Override
    public Auction getAuctionDetail(Long auctionId) {
        return auctionRepository.findById(auctionId)
                .orElseThrow(() -> AuctionNotFoundException.withId(auctionId));
    }
}
