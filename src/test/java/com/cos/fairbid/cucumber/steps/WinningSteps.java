package com.cos.fairbid.cucumber.steps;

import com.cos.fairbid.auction.adapter.out.persistence.entity.AuctionEntity;
import com.cos.fairbid.auction.adapter.out.persistence.repository.JpaAuctionRepository;
import com.cos.fairbid.auction.domain.AuctionStatus;
import com.cos.fairbid.bid.adapter.in.dto.PlaceBidRequest;
import com.cos.fairbid.bid.domain.BidType;
import com.cos.fairbid.cucumber.adapter.TestAdapter;
import com.cos.fairbid.cucumber.adapter.TestContext;
import com.cos.fairbid.winning.application.port.in.CloseAuctionUseCase;
import com.cos.fairbid.winning.application.port.out.WinningRepository;
import com.cos.fairbid.winning.domain.Winning;
import io.cucumber.java.ko.그러면;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만약;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 낙찰 기능 Step Definitions
 */
public class WinningSteps {

    private final TestAdapter testAdapter;
    private final TestContext testContext;

    @Autowired
    private CloseAuctionUseCase closeAuctionUseCase;

    @Autowired
    private JpaAuctionRepository jpaAuctionRepository;

    @Autowired
    private WinningRepository winningRepository;

    @Autowired
    private EntityManager entityManager;

    public WinningSteps(TestAdapter testAdapter, TestContext testContext) {
        this.testAdapter = testAdapter;
        this.testContext = testContext;
    }

    @그리고("구매자A가 {long}원으로 직접 입찰을 요청한다")
    public void 구매자A가_금액으로_직접_입찰을_요청한다(Long amount) {
        // Given: 구매자 A가 입찰 (bidderId = 2)
        placeBid(amount, 2L);
    }

    @그리고("구매자B가 {long}원으로 직접 입찰을 요청한다")
    public void 구매자B가_금액으로_직접_입찰을_요청한다(Long amount) {
        // Given: 구매자 B가 입찰 (bidderId = 3)
        placeBid(amount, 3L);
    }

    private void placeBid(Long amount, Long bidderId) {
        Long auctionId = testContext.getLastCreatedAuctionId();

        PlaceBidRequest request = PlaceBidRequest.builder()
                .amount(amount)
                .bidType(BidType.DIRECT)
                .build();

        String url = "/api/v1/auctions/" + auctionId + "/bids?bidderId=" + bidderId;
        ResponseEntity<Map> response = testAdapter.post(url, request, Map.class);
        testContext.setLastResponse(response);
    }

    @만약("경매 종료 처리를 실행한다")
    @Transactional
    public void 경매_종료_처리를_실행한다() {
        // When: 경매 종료 시간을 과거로 변경하고 종료 처리 실행
        Long auctionId = testContext.getLastCreatedAuctionId();

        // 종료 시간을 1초 전으로 변경 (JPQL로 직접 업데이트)
        entityManager.createQuery(
                        "UPDATE AuctionEntity a SET a.scheduledEndTime = :endTime WHERE a.id = :id")
                .setParameter("endTime", LocalDateTime.now().minusSeconds(1))
                .setParameter("id", auctionId)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        // 경매 종료 처리 실행
        closeAuctionUseCase.closeExpiredAuctions();
    }

    @그러면("경매 상태가 {string}로 변경된다")
    public void 경매_상태가_변경된다(String expectedStatus) {
        // Then: 경매 상태 확인
        Long auctionId = testContext.getLastCreatedAuctionId();

        AuctionEntity auctionEntity = jpaAuctionRepository.findById(auctionId)
                .orElseThrow(() -> new RuntimeException("경매를 찾을 수 없습니다."));

        assertThat(auctionEntity.getStatus().name()).isEqualTo(expectedStatus);
    }

    @그리고("1순위 낙찰 정보가 생성된다")
    public void 일순위_낙찰_정보가_생성된다() {
        // Then: 1순위 Winning 정보 확인
        Long auctionId = testContext.getLastCreatedAuctionId();

        List<Winning> winnings = winningRepository.findByAuctionId(auctionId);
        assertThat(winnings).isNotEmpty();

        Winning firstRank = winnings.stream()
                .filter(w -> w.getRank() == 1)
                .findFirst()
                .orElse(null);

        assertThat(firstRank).isNotNull();
        assertThat(firstRank.getPaymentDeadline()).isNotNull();
    }

    @그리고("2순위 낙찰 후보 정보가 생성된다")
    public void 이순위_낙찰_후보_정보가_생성된다() {
        // Then: 2순위 Winning 정보 확인
        Long auctionId = testContext.getLastCreatedAuctionId();

        List<Winning> winnings = winningRepository.findByAuctionId(auctionId);

        Winning secondRank = winnings.stream()
                .filter(w -> w.getRank() == 2)
                .findFirst()
                .orElse(null);

        assertThat(secondRank).isNotNull();
        assertThat(secondRank.getPaymentDeadline()).isNull(); // 승계 전이므로 null
    }
}
