package com.cos.fairbid.cucumber.steps;

import com.cos.fairbid.cucumber.adapter.TestAdapter;
import com.cos.fairbid.cucumber.adapter.TestContext;
import com.cos.fairbid.transaction.application.port.out.TransactionRepositoryPort;
import com.cos.fairbid.transaction.domain.Transaction;
import com.cos.fairbid.transaction.domain.TransactionStatus;
import com.cos.fairbid.winning.application.port.in.ProcessNoShowUseCase;
import com.cos.fairbid.winning.application.port.out.WinningRepositoryPort;
import com.cos.fairbid.winning.domain.Winning;
import com.cos.fairbid.winning.domain.WinningStatus;
import io.cucumber.java.ko.그러면;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만약;
import io.cucumber.java.ko.조건;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제 기능 Step Definitions
 *
 * 테스트 사용자 ID 규칙:
 * - 판매자: sellerId = 1
 * - 구매자(기본): bidderId = 2
 * - 구매자A: bidderId = 2
 * - 구매자B: bidderId = 3
 * - 다른 사용자: userId = 999
 */
public class PaymentSteps {

    private final TestAdapter testAdapter;
    private final TestContext testContext;

    @Autowired
    private TransactionRepositoryPort transactionRepositoryPort;

    @Autowired
    private WinningRepositoryPort winningRepositoryPort;

    @Autowired
    private ProcessNoShowUseCase processNoShowUseCase;

    /** 현재 테스트에서 사용 중인 Transaction */
    private Transaction currentTransaction;

    /** 1순위 낙찰자 ID (기본값: 2, 구매자A) */
    private Long firstRankBidderId = 2L;

    /** 2순위 낙찰자 ID (구매자B: 3) */
    private Long secondRankBidderId = 3L;

    public PaymentSteps(TestAdapter testAdapter, TestContext testContext) {
        this.testAdapter = testAdapter;
        this.testContext = testContext;
    }

    // =========================================================================
    // Transaction 생성 및 상태 검증
    // =========================================================================

    @그리고("Transaction이 생성되고 상태가 {string}이다")
    public void transaction이_생성되고_상태가_이다(String expectedStatus) {
        // Then: Transaction 생성 및 상태 확인
        Long auctionId = testContext.getLastCreatedAuctionId();

        Optional<Transaction> transactionOpt = transactionRepositoryPort.findByAuctionId(auctionId);
        assertThat(transactionOpt).isPresent();

        currentTransaction = transactionOpt.get();
        assertThat(currentTransaction.getStatus().name()).isEqualTo(expectedStatus);
    }

    @그리고("Transaction이 생성되고 구매자가 1순위이다")
    public void transaction이_생성되고_구매자가_1순위이다() {
        // Then: Transaction이 1순위 낙찰자로 생성되었는지 확인
        Long auctionId = testContext.getLastCreatedAuctionId();

        // 1순위 Winning 조회
        Optional<Winning> firstRankOpt = winningRepositoryPort.findByAuctionIdAndRank(auctionId, 1);
        assertThat(firstRankOpt).isPresent();
        firstRankBidderId = firstRankOpt.get().getBidderId();

        // 2순위 Winning 조회 (있는 경우)
        Optional<Winning> secondRankOpt = winningRepositoryPort.findByAuctionIdAndRank(auctionId, 2);
        secondRankOpt.ifPresent(winning -> secondRankBidderId = winning.getBidderId());

        // Transaction 확인
        Optional<Transaction> transactionOpt = transactionRepositoryPort.findByAuctionId(auctionId);
        assertThat(transactionOpt).isPresent();

        currentTransaction = transactionOpt.get();
        assertThat(currentTransaction.getBuyerId()).isEqualTo(firstRankBidderId);
    }

    @그리고("Transaction 상태가 {string}로 변경된다")
    public void transaction_상태가_로_변경된다(String expectedStatus) {
        // Then: Transaction 상태 확인
        Long auctionId = testContext.getLastCreatedAuctionId();

        Transaction transaction = transactionRepositoryPort.findByAuctionId(auctionId)
                .orElseThrow(() -> new AssertionError("Transaction을 찾을 수 없습니다."));

        assertThat(transaction.getStatus().name()).isEqualTo(expectedStatus);
    }

    @그리고("Transaction의 구매자가 2순위로 변경됨")
    public void transaction의_구매자가_2순위로_변경됨() {
        // Then: Transaction 구매자가 2순위 입찰자로 변경되었는지 확인
        Long auctionId = testContext.getLastCreatedAuctionId();

        Transaction transaction = transactionRepositoryPort.findByAuctionId(auctionId)
                .orElseThrow(() -> new AssertionError("Transaction을 찾을 수 없습니다."));

        assertThat(transaction.getBuyerId()).isEqualTo(secondRankBidderId);
        currentTransaction = transaction;
    }

    // =========================================================================
    // Winning 상태 검증
    // =========================================================================

    @그리고("Winning 상태가 {string}로 변경된다")
    public void winning_상태가_로_변경된다(String expectedStatus) {
        // Then: 1순위 Winning 상태 확인
        Long auctionId = testContext.getLastCreatedAuctionId();

        Winning winning = winningRepositoryPort.findByAuctionIdAndRank(auctionId, 1)
                .orElseThrow(() -> new AssertionError("1순위 Winning을 찾을 수 없습니다."));

        assertThat(winning.getStatus().name()).isEqualTo(expectedStatus);
    }

    @그러면("1순위 Winning 상태가 {string}이다")
    public void 일순위_winning_상태가_이다(String expectedStatus) {
        // Then: 1순위 Winning 상태 확인
        Long auctionId = testContext.getLastCreatedAuctionId();

        Winning winning = winningRepositoryPort.findByAuctionIdAndRank(auctionId, 1)
                .orElseThrow(() -> new AssertionError("1순위 Winning을 찾을 수 없습니다."));

        assertThat(winning.getStatus().name()).isEqualTo(expectedStatus);
    }

    @그리고("2순위 Winning 상태가 {string}이다")
    public void 이순위_winning_상태가_이다(String expectedStatus) {
        // Then: 2순위 Winning 상태 확인
        Long auctionId = testContext.getLastCreatedAuctionId();

        Winning winning = winningRepositoryPort.findByAuctionIdAndRank(auctionId, 2)
                .orElseThrow(() -> new AssertionError("2순위 Winning을 찾을 수 없습니다."));

        assertThat(winning.getStatus().name()).isEqualTo(expectedStatus);
    }

    @그리고("2순위에게 결제 권한이 승계됨")
    public void 이순위에게_결제_권한이_승계됨() {
        // Then: 2순위 Winning에 결제 기한이 설정되었는지 확인
        Long auctionId = testContext.getLastCreatedAuctionId();

        Winning secondWinning = winningRepositoryPort.findByAuctionIdAndRank(auctionId, 2)
                .orElseThrow(() -> new AssertionError("2순위 Winning을 찾을 수 없습니다."));

        // 승계 시 paymentDeadline이 설정됨
        assertThat(secondWinning.getPaymentDeadline()).isNotNull();
        assertThat(secondWinning.getStatus()).isEqualTo(WinningStatus.PENDING_PAYMENT);
    }

    // =========================================================================
    // 결제 기한 조작
    // =========================================================================

    @조건("결제 기한이 만료됨")
    public void 결제_기한이_만료됨() {
        // Given: Transaction의 결제 기한을 과거로 변경
        Long auctionId = testContext.getLastCreatedAuctionId();

        Transaction transaction = transactionRepositoryPort.findByAuctionId(auctionId)
                .orElseThrow(() -> new AssertionError("Transaction을 찾을 수 없습니다."));

        // 결제 기한을 1초 전으로 설정
        LocalDateTime expiredDeadline = LocalDateTime.now().minusSeconds(1);
        Transaction expiredTransaction = Transaction.reconstitute()
                .id(transaction.getId())
                .auctionId(transaction.getAuctionId())
                .sellerId(transaction.getSellerId())
                .buyerId(transaction.getBuyerId())
                .finalPrice(transaction.getFinalPrice())
                .status(transaction.getStatus())
                .paymentDeadline(expiredDeadline)
                .createdAt(transaction.getCreatedAt())
                .paidAt(transaction.getPaidAt())
                .reminderSent(transaction.isReminderSent())
                .build();

        transactionRepositoryPort.save(expiredTransaction);
        currentTransaction = expiredTransaction;
    }

    @조건("1순위 결제 기한이 만료됨")
    public void 일순위_결제_기한이_만료됨() {
        // Given: 1순위 Winning의 결제 기한을 과거로 변경
        Long auctionId = testContext.getLastCreatedAuctionId();

        // 1순위 Winning 조회 및 결제 기한 만료 처리
        Winning firstWinning = winningRepositoryPort.findByAuctionIdAndRank(auctionId, 1)
                .orElseThrow(() -> new AssertionError("1순위 Winning을 찾을 수 없습니다."));

        LocalDateTime expiredDeadline = LocalDateTime.now().minusSeconds(1);
        Winning expiredWinning = Winning.reconstitute()
                .id(firstWinning.getId())
                .auctionId(firstWinning.getAuctionId())
                .rank(firstWinning.getRank())
                .bidderId(firstWinning.getBidderId())
                .bidAmount(firstWinning.getBidAmount())
                .status(firstWinning.getStatus())
                .paymentDeadline(expiredDeadline)
                .createdAt(firstWinning.getCreatedAt())
                .build();

        winningRepositoryPort.save(expiredWinning);

        // Transaction 결제 기한도 함께 만료
        결제_기한이_만료됨();
    }

    // =========================================================================
    // 결제 요청
    // =========================================================================

    @만약("낙찰자가 결제를 요청한다")
    public void 낙찰자가_결제를_요청한다() {
        // When: 낙찰자(기본: bidderId=2)가 결제 요청
        Long auctionId = testContext.getLastCreatedAuctionId();

        Transaction transaction = transactionRepositoryPort.findByAuctionId(auctionId)
                .orElseThrow(() -> new AssertionError("Transaction을 찾을 수 없습니다."));

        Long transactionId = transaction.getId();
        Long buyerId = transaction.getBuyerId();

        String url = "/api/v1/transactions/" + transactionId + "/payment?userId=" + buyerId;
        ResponseEntity<Map> response = testAdapter.post(url, null, Map.class);
        testContext.setLastResponse(response);
    }

    @만약("다른 사용자가 결제를 요청한다")
    public void 다른_사용자가_결제를_요청한다() {
        // When: 다른 사용자(userId=999)가 결제 요청
        Long auctionId = testContext.getLastCreatedAuctionId();

        Transaction transaction = transactionRepositoryPort.findByAuctionId(auctionId)
                .orElseThrow(() -> new AssertionError("Transaction을 찾을 수 없습니다."));

        Long transactionId = transaction.getId();
        Long otherUserId = 999L;

        String url = "/api/v1/transactions/" + transactionId + "/payment?userId=" + otherUserId;
        ResponseEntity<Map> response = testAdapter.post(url, null, Map.class);
        testContext.setLastResponse(response);
    }

    @만약("2순위가 결제를 요청한다")
    public void 이순위가_결제를_요청한다() {
        // When: 2순위 낙찰자가 결제 요청
        Long auctionId = testContext.getLastCreatedAuctionId();

        Transaction transaction = transactionRepositoryPort.findByAuctionId(auctionId)
                .orElseThrow(() -> new AssertionError("Transaction을 찾을 수 없습니다."));

        Long transactionId = transaction.getId();
        // 승계 후 buyerId가 2순위로 변경되어 있음
        Long buyerId = transaction.getBuyerId();

        String url = "/api/v1/transactions/" + transactionId + "/payment?userId=" + buyerId;
        ResponseEntity<Map> response = testAdapter.post(url, null, Map.class);
        testContext.setLastResponse(response);
    }

    // =========================================================================
    // 노쇼 처리
    // =========================================================================

    @만약("노쇼 처리가 실행됨")
    public void 노쇼_처리가_실행됨() {
        // When: 결제 기한 만료된 건들에 대해 노쇼 처리 실행
        processNoShowUseCase.processExpiredPayments();
    }
}
