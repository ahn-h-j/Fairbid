package com.cos.fairbid.transaction.adapter.out.persistence.mapper;

import com.cos.fairbid.transaction.adapter.out.persistence.entity.TransactionEntity;
import com.cos.fairbid.transaction.domain.Transaction;
import org.springframework.stereotype.Component;

/**
 * Transaction Entity <-> Domain 변환 매퍼
 * 영속성 계층과 도메인 계층 간의 변환 책임을 가진다
 */
@Component
public class TransactionMapper {

    /**
     * Domain -> Entity 변환
     *
     * @param transaction 도메인 객체
     * @return JPA 엔티티
     */
    public TransactionEntity toEntity(Transaction transaction) {
        return TransactionEntity.builder()
                .id(transaction.getId())
                .auctionId(transaction.getAuctionId())
                .sellerId(transaction.getSellerId())
                .buyerId(transaction.getBuyerId())
                .finalPrice(transaction.getFinalPrice())
                .status(transaction.getStatus())
                .paymentDeadline(transaction.getPaymentDeadline())
                .createdAt(transaction.getCreatedAt())
                .paidAt(transaction.getPaidAt())
                .reminderSent(transaction.isReminderSent())
                .build();
    }

    /**
     * Entity -> Domain 변환
     *
     * @param entity JPA 엔티티
     * @return 도메인 객체
     */
    public Transaction toDomain(TransactionEntity entity) {
        return Transaction.reconstitute()
                .id(entity.getId())
                .auctionId(entity.getAuctionId())
                .sellerId(entity.getSellerId())
                .buyerId(entity.getBuyerId())
                .finalPrice(entity.getFinalPrice())
                .status(entity.getStatus())
                .paymentDeadline(entity.getPaymentDeadline())
                .createdAt(entity.getCreatedAt())
                .paidAt(entity.getPaidAt())
                .reminderSent(entity.isReminderSent())
                .build();
    }
}
