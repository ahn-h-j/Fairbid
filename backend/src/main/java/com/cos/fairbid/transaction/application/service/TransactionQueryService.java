package com.cos.fairbid.transaction.application.service;

import com.cos.fairbid.transaction.adapter.in.dto.TransactionDetailResponse;
import com.cos.fairbid.transaction.adapter.in.dto.TransactionSummaryResponse;
import com.cos.fairbid.transaction.application.port.in.TransactionQueryUseCase;
import com.cos.fairbid.transaction.application.port.out.TransactionRepositoryPort;
import com.cos.fairbid.transaction.domain.Transaction;
import com.cos.fairbid.transaction.domain.exception.TransactionNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 거래 조회 서비스
 * TransactionQueryUseCase 구현체로서 거래 조회 로직을 수행한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionQueryService implements TransactionQueryUseCase {

    private final TransactionRepositoryPort transactionRepositoryPort;

    /**
     * 거래 상세 정보를 조회한다
     *
     * @param transactionId 거래 ID
     * @return 거래 상세 응답
     * @throws TransactionNotFoundException 거래를 찾을 수 없는 경우
     */
    @Override
    public TransactionDetailResponse getTransaction(Long transactionId) {
        log.debug("거래 상세 조회 - 거래 ID: {}", transactionId);

        Transaction transaction = transactionRepositoryPort.findById(transactionId)
                .orElseThrow(() -> TransactionNotFoundException.withId(transactionId));

        return TransactionDetailResponse.from(transaction);
    }

    /**
     * 내 판매 내역을 조회한다
     *
     * @param sellerId 판매자 ID
     * @return 판매 내역 요약 목록
     */
    @Override
    public List<TransactionSummaryResponse> getMySales(Long sellerId) {
        log.debug("판매 내역 조회 - 판매자 ID: {}", sellerId);

        return transactionRepositoryPort.findBySellerId(sellerId)
                .stream()
                .map(TransactionSummaryResponse::from)
                .toList();
    }
}
