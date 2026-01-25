package com.cos.fairbid.transaction.adapter.out.persistence.entity;

import com.cos.fairbid.transaction.domain.TransactionStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 거래 JPA 엔티티
 * DB 테이블 매핑 전용 (비즈니스 로직 금지)
 */
@Entity
@Table(name = "transaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 경매 ID */
    @Column(name = "auction_id", nullable = false)
    private Long auctionId;

    /** 판매자 ID */
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    /** 구매자(낙찰자) ID */
    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    /** 최종 낙찰가 */
    @Column(name = "final_price", nullable = false)
    private Long finalPrice;

    /** 거래 상태 (AWAITING_PAYMENT, PAID, CANCELLED) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    /** 결제 마감 일시 */
    @Column(name = "payment_deadline")
    private LocalDateTime paymentDeadline;

    /** 거래 생성 일시 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 결제 완료 일시 */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    /** 리마인더 발송 여부 */
    @Column(name = "reminder_sent", nullable = false)
    private boolean reminderSent;

    @Builder
    private TransactionEntity(
            Long id,
            Long auctionId,
            Long sellerId,
            Long buyerId,
            Long finalPrice,
            TransactionStatus status,
            LocalDateTime paymentDeadline,
            LocalDateTime createdAt,
            LocalDateTime paidAt,
            boolean reminderSent
    ) {
        this.id = id;
        this.auctionId = auctionId;
        this.sellerId = sellerId;
        this.buyerId = buyerId;
        this.finalPrice = finalPrice;
        this.status = status;
        this.paymentDeadline = paymentDeadline;
        this.createdAt = createdAt;
        this.paidAt = paidAt;
        this.reminderSent = reminderSent;
    }
}
