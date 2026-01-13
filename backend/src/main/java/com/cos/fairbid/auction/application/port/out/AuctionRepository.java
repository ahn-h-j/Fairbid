package com.cos.fairbid.auction.application.port.out;

import com.cos.fairbid.auction.application.port.in.AuctionSearchCondition;
import com.cos.fairbid.auction.domain.Auction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
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

    /**
     * 종료 시간이 도래한 진행 중인 경매 목록을 조회한다
     * status = BIDDING 이고 scheduledEndTime <= now
     *
     * @return 종료 대상 경매 목록
     */
    List<Auction> findClosingAuctions();

    /**
     * 검색 조건에 따라 경매 목록을 페이지네이션하여 조회한다
     *
     * @param condition 검색 조건 (status, keyword)
     * @param pageable  페이지네이션 정보
     * @return 경매 목록 (페이지)
     */
    Page<Auction> findAll(AuctionSearchCondition condition, Pageable pageable);
}
