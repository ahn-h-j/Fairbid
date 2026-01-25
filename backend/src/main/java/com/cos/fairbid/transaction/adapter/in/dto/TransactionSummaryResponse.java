package com.cos.fairbid.transaction.adapter.in.dto;

import com.cos.fairbid.transaction.domain.Transaction;

import java.time.LocalDateTime;

/**
 * 거래 요약 조회 응답 DTO (판매 내역 목록용)
 *
 * @param transactionId 거래 ID
 * @param auctionId     경매 ID
 * @param finalPrice    최종 낙찰가
 * @param status        거래 상태
 * @param createdAt     거래 생성 일시
 */
public record TransactionSummaryResponse(
        Long transactionId,
        Long auctionId,
        Long finalPrice,
        String status,
        LocalDateTime createdAt
) {

    /**
     * 도메인 객체로부터 요약 응답 DTO를 생성한다
     *
     * @param transaction 거래 도메인 객체
     * @return TransactionSummaryResponse
     */
    public static TransactionSummaryResponse from(Transaction transaction) {
        return new TransactionSummaryResponse(
                transaction.getId(),
                transaction.getAuctionId(),
                transaction.getFinalPrice(),
                transaction.getStatus().name(),
                transaction.getCreatedAt()
        );
    }
}
