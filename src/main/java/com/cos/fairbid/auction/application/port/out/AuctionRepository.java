package com.cos.fairbid.auction.application.port.out;

import com.cos.fairbid.auction.domain.Auction;

import java.util.Optional;

/**
 * 경매 저장소 아웃바운드 포트
 * 영속성 계층과의 통신을 위한 인터페이스
 */
public interface AuctionRepository {

    /**
     * 경매를 저장한다
     *
     * @param auction 저장할 경매 도메인 객체
     * @return 저장된 경매 (ID 포함)
     */
    Auction save(Auction auction);

    /**
     * ID로 경매를 조회한다
     *
     * @param id 경매 ID
     * @return 경매 도메인 객체 (Optional)
     */
    Optional<Auction> findById(Long id);

    /**
     * ID로 경매를 조회하며 비관적 락(FOR UPDATE)을 획득한다
     * 동시성 제어가 필요한 입찰 처리 시 사용
     *
     * @param id 경매 ID
     * @return 경매 도메인 객체 (Optional)
     */
    Optional<Auction> findByIdWithLock(Long id);
}
