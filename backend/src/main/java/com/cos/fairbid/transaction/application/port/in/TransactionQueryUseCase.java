package com.cos.fairbid.transaction.application.port.in;

import com.cos.fairbid.transaction.adapter.in.dto.TransactionDetailResponse;
import com.cos.fairbid.transaction.adapter.in.dto.TransactionSummaryResponse;

import java.util.List;

/**
 * 거래 조회 유스케이스 인바운드 포트
 * 거래 상세 조회 및 판매 내역 조회를 위한 인터페이스
 */
public interface TransactionQueryUseCase {

    /**
     * 거래 상세 정보를 조회한다
     *
     * @param transactionId 거래 ID
     * @return 거래 상세 응답
     */
    TransactionDetailResponse getTransaction(Long transactionId);

    /**
     * 내 판매 내역을 조회한다
     *
     * @param sellerId 판매자 ID
     * @return 판매 내역 요약 목록
     */
    List<TransactionSummaryResponse> getMySales(Long sellerId);

    /**
     * 경매 ID로 거래 정보를 조회한다
     *
     * @param auctionId 경매 ID
     * @return 거래 상세 응답 (없으면 null)
     */
    TransactionDetailResponse getTransactionByAuctionId(Long auctionId);
}
