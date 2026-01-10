package com.cos.fairbid.auction.adapter.out.persistence.repository;

import com.cos.fairbid.auction.adapter.out.persistence.entity.AuctionEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 경매 JPA Repository
 * Spring Data JPA 인터페이스
 */
public interface JpaAuctionRepository extends JpaRepository<AuctionEntity, Long> {

    /**
     * ID로 경매를 조회하며 비관적 락을 획득한다
     * 동시성 제어가 필요한 입찰 처리 시 사용
     *
     * @param id 경매 ID
     * @return 경매 엔티티 (Optional)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AuctionEntity a WHERE a.id = :id")
    Optional<AuctionEntity> findByIdWithLock(@Param("id") Long id);
}
