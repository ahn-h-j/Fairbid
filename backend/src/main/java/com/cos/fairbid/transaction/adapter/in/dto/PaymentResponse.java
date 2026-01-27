package com.cos.fairbid.transaction.adapter.in.dto;

import com.cos.fairbid.transaction.domain.Transaction;

import java.time.LocalDateTime;

/**
 * 결제 처리 결과 응답 DTO
 *
 * @param transactionId 거래 ID
 * @param auctionId     경매 ID
 * @param status        거래 상태
 * @param finalPrice    최종 낙찰가
 * @param paidAt        결제 완료 일시
 */
public record PaymentResponse(
        Long transactionId,
        Long auctionId,
        String status,
        Long finalPrice,
        LocalDateTime paidAt
) {

    /**
     * 도메인 객체로부터 응답 DTO를 생성한다
     *
     * @param transaction 거래 도메인 객체
     * @return PaymentResponse
     */
    public static PaymentResponse from(Transaction transaction) {
        return new PaymentResponse(
                transaction.getId(),
                transaction.getAuctionId(),
                transaction.getStatus().name(),
                transaction.getFinalPrice(),
                transaction.getPaidAt()
        );
    }
}
