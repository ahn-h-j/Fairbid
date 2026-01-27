package com.cos.fairbid.transaction.adapter.out.persistence;

import com.cos.fairbid.transaction.adapter.out.persistence.entity.TransactionEntity;
import com.cos.fairbid.transaction.adapter.out.persistence.mapper.TransactionMapper;
import com.cos.fairbid.transaction.adapter.out.persistence.repository.JpaTransactionRepository;
import com.cos.fairbid.transaction.application.port.out.TransactionRepositoryPort;
import com.cos.fairbid.transaction.domain.Transaction;
import com.cos.fairbid.transaction.domain.TransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 거래 영속성 어댑터
 * TransactionRepositoryPort 포트 구현체
 */
@Repository
@RequiredArgsConstructor
public class TransactionPersistenceAdapter implements TransactionRepositoryPort {

    private final JpaTransactionRepository jpaTransactionRepository;
    private final TransactionMapper transactionMapper;

    @Override
    public Transaction save(Transaction transaction) {
        TransactionEntity entity = transactionMapper.toEntity(transaction);
        TransactionEntity savedEntity = jpaTransactionRepository.save(entity);
        return transactionMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Transaction> findById(Long id) {
        return jpaTransactionRepository.findById(id)
                .map(transactionMapper::toDomain);
    }

    @Override
    public Optional<Transaction> findByAuctionId(Long auctionId) {
        return jpaTransactionRepository.findByAuctionId(auctionId)
                .map(transactionMapper::toDomain);
    }

    @Override
    public List<Transaction> findBySellerId(Long sellerId) {
        return jpaTransactionRepository.findBySellerId(sellerId)
                .stream()
                .map(transactionMapper::toDomain)
                .toList();
    }

    /**
     * 리마인더 발송 대상 조회
     * deadline - 1h <= now 조건을 deadline <= now + 1h 로 변환하여 쿼리
     */
    @Override
    public List<Transaction> findReminderTargets() {
        LocalDateTime thresholdTime = LocalDateTime.now().plusHours(1);
        return jpaTransactionRepository.findReminderTargets(
                        TransactionStatus.AWAITING_PAYMENT,
                        thresholdTime
                )
                .stream()
                .map(transactionMapper::toDomain)
                .toList();
    }
}
