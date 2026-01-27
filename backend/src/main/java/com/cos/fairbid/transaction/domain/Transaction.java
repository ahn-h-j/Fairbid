package com.cos.fairbid.transaction.domain;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 거래 도메인 모델
 * 낙찰 후 결제까지의 거래 정보를 관리한다
 */
@Getter
@Builder
public class Transaction {

    private Long id;
    private Long auctionId;
    private Long sellerId;
    private Long buyerId;
    private Long finalPrice;
    private TransactionStatus status;
    private LocalDateTime paymentDeadline;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;

    /** 리마인더 발송 여부 (중복 발송 방지) */
    private boolean reminderSent;

    /**
     * 낙찰 시 거래 생성
     * 1순위 낙찰자 확정 시점에 호출된다
     *
     * @param auctionId       경매 ID
     * @param sellerId        판매자 ID
     * @param buyerId         낙찰자(구매자) ID
     * @param finalPrice      최종 낙찰가
     * @param paymentDeadline 결제 마감일시
     * @return 결제 대기 상태의 Transaction 객체
     * @throws IllegalArgumentException 필수 파라미터가 null인 경우
     */
    public static Transaction create(Long auctionId, Long sellerId, Long buyerId,
                                     Long finalPrice, LocalDateTime paymentDeadline) {
        validateNotNull(auctionId, "경매 ID");
        validateNotNull(sellerId, "판매자 ID");
        validateNotNull(buyerId, "구매자 ID");
        validateNotNull(finalPrice, "낙찰가");
        validateNotNull(paymentDeadline, "결제 마감일시");

        return Transaction.builder()
                .auctionId(auctionId)
                .sellerId(sellerId)
                .buyerId(buyerId)
                .finalPrice(finalPrice)
                .status(TransactionStatus.AWAITING_PAYMENT)
                .paymentDeadline(paymentDeadline)
                .createdAt(LocalDateTime.now())
                .paidAt(null)
                .reminderSent(false)
                .build();
    }

    /**
     * 영속성 계층에서 조회한 데이터로 도메인 객체 복원
     */
    public static TransactionBuilder reconstitute() {
        return Transaction.builder();
    }

    // =====================================================
    // 비즈니스 로직 메서드
    // =====================================================

    /**
     * 결제를 완료 처리한다
     * 상태를 PAID로 변경하고 결제 일시를 기록한다
     *
     * @throws IllegalStateException 결제 대기 상태가 아닌 경우
     */
    public void markAsPaid() {
        if (this.status != TransactionStatus.AWAITING_PAYMENT) {
            throw new IllegalStateException(
                    "결제 대기 상태에서만 결제 완료 처리가 가능합니다. 현재 상태: " + this.status);
        }
        this.status = TransactionStatus.PAID;
        this.paidAt = LocalDateTime.now();
    }

    /**
     * 거래를 취소 처리한다
     * 노쇼, 유찰 등의 사유로 거래가 무효화될 때 호출된다
     *
     * @throws IllegalStateException 결제 대기 상태가 아닌 경우
     */
    public void cancel() {
        if (this.status != TransactionStatus.AWAITING_PAYMENT) {
            throw new IllegalStateException(
                    "결제 대기 상태에서만 취소 처리가 가능합니다. 현재 상태: " + this.status);
        }
        this.status = TransactionStatus.CANCELLED;
    }

    /**
     * 2순위 승계 시 구매자 정보를 갱신한다
     * 기존 1순위 노쇼 후 2순위에게 구매 권한이 넘어갈 때 호출된다
     *
     * @param newBuyerId         2순위 입찰자 ID
     * @param newFinalPrice      2순위 입찰 금액
     * @param newPaymentDeadline 새 결제 마감일시 (1시간)
     * @throws IllegalStateException 결제 대기 상태가 아닌 경우
     */
    public void transferToSecondRank(Long newBuyerId, Long newFinalPrice,
                                     LocalDateTime newPaymentDeadline) {
        if (this.status != TransactionStatus.AWAITING_PAYMENT) {
            throw new IllegalStateException(
                    "결제 대기 상태에서만 2순위 승계가 가능합니다. 현재 상태: " + this.status);
        }
        validateNotNull(newBuyerId, "2순위 구매자 ID");
        validateNotNull(newFinalPrice, "2순위 낙찰가");
        validateNotNull(newPaymentDeadline, "새 결제 마감일시");

        this.buyerId = newBuyerId;
        this.finalPrice = newFinalPrice;
        this.paymentDeadline = newPaymentDeadline;
        this.status = TransactionStatus.AWAITING_PAYMENT;
        this.reminderSent = false;
    }

    /**
     * 결제 기한이 만료되었는지 확인한다
     *
     * @return 만료되었으면 true
     */
    public boolean isPaymentExpired() {
        if (paymentDeadline == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(paymentDeadline);
    }

    /**
     * 결제 대기 중인지 확인한다
     *
     * @return 결제 대기 중이면 true
     */
    public boolean isAwaitingPayment() {
        return this.status == TransactionStatus.AWAITING_PAYMENT;
    }

    /**
     * 리마인더 발송 대상인지 확인한다
     * 결제 대기 중이고, 아직 리마인더를 보내지 않았으며, 마감 1시간 이내인 경우
     *
     * @return 리마인더 발송 대상이면 true
     */
    public boolean isReminderTarget() {
        if (this.status != TransactionStatus.AWAITING_PAYMENT) {
            return false;
        }
        if (this.reminderSent) {
            return false;
        }
        if (this.paymentDeadline == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reminderThreshold = this.paymentDeadline.minusHours(1);
        // 마감 1시간 전 ~ 마감 전 사이에만 리마인더 발송
        return now.isAfter(reminderThreshold) && now.isBefore(this.paymentDeadline);
    }

    /**
     * 리마인더 발송 완료를 기록한다
     */
    public void markReminderSent() {
        this.reminderSent = true;
    }

    /**
     * 해당 사용자가 이 거래의 구매자인지 확인한다
     *
     * @param userId 확인할 사용자 ID
     * @return 구매자이면 true
     */
    public boolean isBuyer(Long userId) {
        return this.buyerId != null && this.buyerId.equals(userId);
    }

    /**
     * 해당 사용자가 이 거래의 판매자인지 확인한다
     *
     * @param userId 확인할 사용자 ID
     * @return 판매자이면 true
     */
    public boolean isSeller(Long userId) {
        return this.sellerId != null && this.sellerId.equals(userId);
    }

    // =====================================================
    // 테스트 전용 메서드
    // =====================================================

    /**
     * [테스트 전용] 결제 기한을 강제로 만료시킨다.
     * 노쇼 처리 테스트를 위해 deadline을 과거로 설정한다.
     */
    public void expirePaymentDeadlineForTest() {
        this.paymentDeadline = LocalDateTime.now().minusHours(1);
    }

    // =====================================================
    // private helper
    // =====================================================

    private static void validateNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "는 null일 수 없습니다.");
        }
    }
}
