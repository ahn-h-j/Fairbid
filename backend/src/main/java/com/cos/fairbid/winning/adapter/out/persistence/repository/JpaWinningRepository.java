package com.cos.fairbid.winning.adapter.out.persistence.repository;

import com.cos.fairbid.winning.adapter.out.persistence.entity.WinningEntity;
import com.cos.fairbid.winning.domain.WinningStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 낙찰 Spring Data JPA Repository
 */
public interface JpaWinningRepository extends JpaRepository<WinningEntity, Long> {

    /**
     * 경매 ID로 낙찰 정보를 조회한다
     */
    List<WinningEntity> findByAuctionId(Long auctionId);

    /**
     * 경매 ID와 순위로 낙찰 정보를 조회한다
     */
    Optional<WinningEntity> findByAuctionIdAndRank(Long auctionId, Integer rank);

    /**
     * 결제 기한이 만료된 결제 대기 중인 낙찰 목록을 조회한다
     */
    @Query("SELECT w FROM WinningEntity w " +
            "WHERE w.status = :status " +
            "AND w.paymentDeadline IS NOT NULL " +
            "AND w.paymentDeadline <= :now")
    List<WinningEntity> findExpiredPendingPayments(
            @Param("status") WinningStatus status,
            @Param("now") LocalDateTime now
    );

    /**
     * 경매 ID와 입찰자 ID로 결제 대기 중인 낙찰 정보를 조회한다
     * 결제 처리 시 현재 구매자에 해당하는 PENDING_PAYMENT 상태의 Winning을 찾는다
     */
    @Query("SELECT w FROM WinningEntity w " +
            "WHERE w.auctionId = :auctionId " +
            "AND w.bidderId = :bidderId " +
            "AND w.status = :status")
    Optional<WinningEntity> findByAuctionIdAndBidderIdAndStatus(
            @Param("auctionId") Long auctionId,
            @Param("bidderId") Long bidderId,
            @Param("status") WinningStatus status
    );
}
