package com.cos.fairbid.cucumber.steps;

import com.cos.fairbid.auction.adapter.in.dto.CreateAuctionRequest;
import com.cos.fairbid.auction.domain.Auction;
import com.cos.fairbid.bid.adapter.in.dto.PlaceBidRequest;
import com.cos.fairbid.bid.domain.BidType;
import com.cos.fairbid.cucumber.adapter.TestAdapter;
import com.cos.fairbid.cucumber.adapter.TestContext;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만약;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 입찰 기능 Step Definitions
 */
public class BidSteps {

    private final TestAdapter testAdapter;
    private final TestContext testContext;

    public BidSteps(TestAdapter testAdapter, TestContext testContext) {
        this.testAdapter = testAdapter;
        this.testContext = testContext;
    }

    @만약("구매자가 원터치 입찰을 요청한다")
    public void 구매자가_원터치_입찰을_요청한다() {
        // When: 원터치 입찰 요청 (금액은 자동 계산)
        Long auctionId = testContext.getLastCreatedAuctionId();

        PlaceBidRequest request = PlaceBidRequest.builder()
                .bidType(BidType.ONE_TOUCH)
                .build();

        String url = "/api/v1/auctions/" + auctionId + "/bids";
        ResponseEntity<Map> response = testAdapter.post(url, request, Map.class);
        testContext.setLastResponse(response);
    }

    @만약("구매자가 {long}원으로 직접 입찰을 요청한다")
    public void 구매자가_금액으로_직접_입찰을_요청한다(Long amount) {
        // When: 금액 직접 지정 입찰 요청
        Long auctionId = testContext.getLastCreatedAuctionId();

        PlaceBidRequest request = PlaceBidRequest.builder()
                .amount(amount)
                .bidType(BidType.DIRECT)
                .build();

        String url = "/api/v1/auctions/" + auctionId + "/bids";
        ResponseEntity<Map> response = testAdapter.post(url, request, Map.class);
        testContext.setLastResponse(response);
    }

    @만약("존재하지 않는 경매에 입찰을 요청한다")
    public void 존재하지_않는_경매에_입찰을_요청한다() {
        // When: 존재하지 않는 경매 ID로 입찰 요청
        Long nonExistentId = 999999L;

        PlaceBidRequest request = PlaceBidRequest.builder()
                .bidType(BidType.ONE_TOUCH)
                .build();

        String url = "/api/v1/auctions/" + nonExistentId + "/bids";
        ResponseEntity<Map> response = testAdapter.post(url, request, Map.class);
        testContext.setLastResponse(response);
    }

    @그리고("입찰 금액이 시작가 + 입찰단위와 같다")
    @SuppressWarnings("unchecked")
    public void 입찰_금액이_시작가_플러스_입찰단위와_같다() {
        // Then: 원터치 입찰 금액 = 시작가 + 입찰단위 (동적 계산)
        CreateAuctionRequest auctionRequest = testContext.getLastRequestBody();
        Long startPrice = auctionRequest.startPrice();
        Long bidIncrement = Auction.calculateBidIncrement(startPrice);
        Long expectedAmount = startPrice + bidIncrement;

        ResponseEntity<Map> response = testContext.getLastResponse();
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        Long actualAmount = ((Number) data.get("amount")).longValue();

        assertThat(actualAmount).isEqualTo(expectedAmount);
    }

    @만약("구매자가 즉시 구매를 요청한다")
    public void 구매자가_즉시_구매를_요청한다() {
        // When: 즉시 구매 요청
        Long auctionId = testContext.getLastCreatedAuctionId();

        PlaceBidRequest request = PlaceBidRequest.builder()
                .bidType(BidType.INSTANT_BUY)
                .build();

        String url = "/api/v1/auctions/" + auctionId + "/bids";
        ResponseEntity<Map> response = testAdapter.post(url, request, Map.class);
        testContext.setLastResponse(response);
    }

    @만약("다른 구매자가 즉시 구매를 요청한다")
    public void 다른_구매자가_즉시_구매를_요청한다() {
        // When: 다른 구매자의 즉시 구매 요청 (동일 경매)
        Long auctionId = testContext.getLastCreatedAuctionId();

        PlaceBidRequest request = PlaceBidRequest.builder()
                .bidType(BidType.INSTANT_BUY)
                .build();

        String url = "/api/v1/auctions/" + auctionId + "/bids";
        ResponseEntity<Map> response = testAdapter.post(url, request, Map.class);
        testContext.setLastResponse(response);
    }

    @만약("다른 구매자가 원터치 입찰을 요청한다")
    public void 다른_구매자가_원터치_입찰을_요청한다() {
        // When: 다른 구매자의 원터치 입찰 요청 (동일 경매)
        Long auctionId = testContext.getLastCreatedAuctionId();

        PlaceBidRequest request = PlaceBidRequest.builder()
                .bidType(BidType.ONE_TOUCH)
                .build();

        String url = "/api/v1/auctions/" + auctionId + "/bids";
        ResponseEntity<Map> response = testAdapter.post(url, request, Map.class);
        testContext.setLastResponse(response);
    }
}
