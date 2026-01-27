package com.cos.fairbid.transaction.application.port.out;

import com.cos.fairbid.transaction.domain.Transaction;

import java.util.List;
import java.util.Optional;

/**
 * 거래 저장소 아웃바운드 포트
 * 영속성 계층과의 통신을 위한 인터페이스
 */
public interface TransactionRepositoryPort {

    /**
     * 거래 정보를 저장한다
     *
     * @param transaction 저장할 거래 도메인 객체
     * @return 저장된 거래 (ID 포함)
     */
    Transaction save(Transaction transaction);

    /**
     * ID로 거래 정보를 조회한다
     *
     * @param id 거래 ID
     * @return 거래 도메인 객체 (Optional)
     */
    Optional<Transaction> findById(Long id);

    /**
     * 경매 ID로 거래 정보를 조회한다
     *
     * @param auctionId 경매 ID
     * @return 거래 도메인 객체 (Optional)
     */
    Optional<Transaction> findByAuctionId(Long auctionId);

    /**
     * 판매자 ID로 거래 목록을 조회한다
     *
     * @param sellerId 판매자 ID
     * @return 해당 판매자의 거래 목록
     */
    List<Transaction> findBySellerId(Long sellerId);

    /**
     * 결제 리마인더 발송 대상 거래 목록을 조회한다
     * 조건: AWAITING_PAYMENT 상태이고, reminderSent=false이며, 마감 1시간 이내인 거래
     *
     * @return 리마인더 발송 대상 거래 목록
     */
    List<Transaction> findReminderTargets();
}
