package com.cos.fairbid.transaction.adapter.in.dto;

import com.cos.fairbid.transaction.domain.Transaction;

import java.time.LocalDateTime;

/**
 * 거래 상세 조회 응답 DTO
 *
 * @param transactionId   거래 ID
 * @param auctionId       경매 ID
 * @param sellerId        판매자 ID
 * @param buyerId         구매자(낙찰자) ID
 * @param finalPrice      최종 낙찰가
 * @param status          거래 상태
 * @param paymentDeadline 결제 마감 일시
 * @param createdAt       거래 생성 일시
 * @param paidAt          결제 완료 일시
 */
public record TransactionDetailResponse(
        Long transactionId,
        Long auctionId,
        Long sellerId,
        Long buyerId,
        Long finalPrice,
        String status,
        LocalDateTime paymentDeadline,
        LocalDateTime createdAt,
        LocalDateTime paidAt
) {

    /**
     * 도메인 객체로부터 상세 응답 DTO를 생성한다
     *
     * @param transaction 거래 도메인 객체
     * @return TransactionDetailResponse
     */
    public static TransactionDetailResponse from(Transaction transaction) {
        return new TransactionDetailResponse(
                transaction.getId(),
                transaction.getAuctionId(),
                transaction.getSellerId(),
                transaction.getBuyerId(),
                transaction.getFinalPrice(),
                transaction.getStatus().name(),
                transaction.getPaymentDeadline(),
                transaction.getCreatedAt(),
                transaction.getPaidAt()
        );
    }
}
