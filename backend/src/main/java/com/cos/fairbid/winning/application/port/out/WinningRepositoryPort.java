package com.cos.fairbid.winning.application.port.out;

import com.cos.fairbid.winning.domain.Winning;

import java.util.List;
import java.util.Optional;

/**
 * 낙찰 저장소 아웃바운드 포트
 * 영속성 계층과의 통신을 위한 인터페이스
 */
public interface WinningRepositoryPort {

    /**
     * 낙찰 정보를 저장한다
     *
     * @param winning 저장할 낙찰 도메인 객체
     * @return 저장된 낙찰 (ID 포함)
     */
    Winning save(Winning winning);

    /**
     * 경매 ID로 낙찰 정보를 조회한다
     *
     * @param auctionId 경매 ID
     * @return 해당 경매의 낙찰 정보 목록 (1, 2순위)
     */
    List<Winning> findByAuctionId(Long auctionId);

    /**
     * 경매 ID와 순위로 낙찰 정보를 조회한다
     *
     * @param auctionId 경매 ID
     * @param rank      순위 (1 or 2)
     * @return 낙찰 도메인 객체 (Optional)
     */
    Optional<Winning> findByAuctionIdAndRank(Long auctionId, Integer rank);

    /**
     * 결제 기한이 만료된 결제 대기 중인 낙찰 목록을 조회한다
     *
     * @return 결제 기한 만료된 낙찰 목록
     */
    List<Winning> findExpiredPendingPayments();

    /**
     * ID로 낙찰 정보를 조회한다
     *
     * @param id 낙찰 ID
     * @return 낙찰 도메인 객체 (Optional)
     */
    Optional<Winning> findById(Long id);

    /**
     * 경매 ID와 입찰자 ID로 결제 대기 중인 낙찰 정보를 조회한다
     * 결제 처리 시 현재 Transaction의 구매자에 해당하는 PENDING_PAYMENT 상태의 Winning을 찾을 때 사용
     *
     * @param auctionId 경매 ID
     * @param bidderId  입찰자 ID
     * @return 낙찰 도메인 객체 (Optional)
     */
    Optional<Winning> findPendingByAuctionIdAndBidderId(Long auctionId, Long bidderId);
}
