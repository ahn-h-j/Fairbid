package com.cos.fairbid.bid.adapter.out.persistence.repository;

import com.cos.fairbid.bid.adapter.out.persistence.entity.BidEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 입찰 JPA Repository
 * Spring Data JPA 인터페이스
 */
public interface JpaBidRepository extends JpaRepository<BidEntity, Long> {

    /**
     * 경매의 상위 2개 입찰을 조회한다 (금액 내림차순)
     *
     * @param auctionId 경매 ID
     * @return 상위 2개 입찰 엔티티 목록
     */
    @Query("SELECT b FROM BidEntity b WHERE b.auctionId = :auctionId ORDER BY b.amount DESC LIMIT 2")
    List<BidEntity> findTop2ByAuctionIdOrderByAmountDesc(@Param("auctionId") Long auctionId);
}
