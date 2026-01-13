package com.cos.fairbid.auction.adapter.out.persistence.repository;

import com.cos.fairbid.auction.adapter.out.persistence.entity.AuctionEntity;
import com.cos.fairbid.auction.domain.AuctionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 경매 JPA Repository
 * Spring Data JPA 인터페이스
 * JpaSpecificationExecutor: 동적 쿼리 지원
 */
public interface JpaAuctionRepository extends JpaRepository<AuctionEntity, Long>, JpaSpecificationExecutor<AuctionEntity> {

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

    /**
     * 종료 시간이 도래한 진행 중인 경매 목록을 조회한다
     *
     * @param status 경매 상태 (BIDDING)
     * @param now    현재 시간
     * @return 종료 대상 경매 엔티티 목록
     */
    @Query("SELECT a FROM AuctionEntity a WHERE a.status = :status AND a.scheduledEndTime <= :now")
    List<AuctionEntity> findClosingAuctions(
            @Param("status") AuctionStatus status,
            @Param("now") LocalDateTime now
    );
}
