package com.cos.fairbid.transaction.adapter.out.persistence.repository;

import com.cos.fairbid.transaction.adapter.out.persistence.entity.TransactionEntity;
import com.cos.fairbid.transaction.domain.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 거래 Spring Data JPA Repository
 */
public interface JpaTransactionRepository extends JpaRepository<TransactionEntity, Long> {

    /**
     * 경매 ID로 거래 정보를 조회한다
     *
     * @param auctionId 경매 ID
     * @return 거래 엔티티 (Optional)
     */
    Optional<TransactionEntity> findByAuctionId(Long auctionId);

    /**
     * 판매자 ID로 거래 목록을 조회한다
     *
     * @param sellerId 판매자 ID
     * @return 해당 판매자의 거래 엔티티 목록
     */
    List<TransactionEntity> findBySellerId(Long sellerId);

    /**
     * 결제 리마인더 발송 대상 거래 목록을 조회한다
     * 조건: AWAITING_PAYMENT 상태이고, reminderSent=false이며, 마감 1시간 이내인 거래
     *
     * @param status 거래 상태 (AWAITING_PAYMENT)
     * @param thresholdTime 현재 시각 (deadline - 1h <= now 이므로, now 기준)
     * @return 리마인더 발송 대상 거래 엔티티 목록
     */
    @Query("SELECT t FROM TransactionEntity t " +
            "WHERE t.status = :status " +
            "AND t.reminderSent = false " +
            "AND t.paymentDeadline IS NOT NULL " +
            "AND t.paymentDeadline <= :thresholdTime")
    List<TransactionEntity> findReminderTargets(
            @Param("status") TransactionStatus status,
            @Param("thresholdTime") LocalDateTime thresholdTime
    );
}
